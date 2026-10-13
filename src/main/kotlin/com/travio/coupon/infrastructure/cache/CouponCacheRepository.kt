package com.travio.coupon.infrastructure.cache

import com.travio.coupon.application.CacheMetrics
import com.travio.coupon.application.CouponIssuePolicy
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


private val log = KotlinLogging.logger {}

@Repository
class CouponCacheRepository(
    private val redis: StringRedisTemplate,
    private val mapper: ObjectMapper,
    private val cacheProperties: CacheProperties,
    private val cacheMetrics: CacheMetrics,
) {
    // 현재 코드에서 만약 coupon을 여러개 생성한다고 하면 서버 성능에 따라 ThreadPool 의 thread 수를 늘릴 수 있음.
    private val backgroundExecutor = Executors.newFixedThreadPool(4) { runnable ->
        Thread(runnable, "coupon-cache-swr-refresh").apply{ isDaemon = true }
    }
    private val lookupScript = listLuaScript("lua/cache-single-flight-swr.lua")
    private val releaseLockScript = longLuaScript("lua/release-lock.lua")

    fun getIssuePolicyOrLoad(id: Long, loader: () -> CouponIssuePolicy) =
        getOrLoad(
            cacheKey = "coupon:$id:issue-policy",
            lockKey = "coupon:$id:issue-policy:lock",
            loader = loader,
        )


    private fun getOrLoad(
        cacheKey: String,
        lockKey: String,
        loader: () -> CouponIssuePolicy,
    ): CouponIssuePolicy {
        val token = UUID.randomUUID().toString()

        repeat(MAX_RETRIES) {
            val now = Instant.now().toEpochMilli()
            val result = redis.runForStrings(
                lookupScript,
                listOf(cacheKey,lockKey),
                now,cacheProperties.freshMs,token,LOCK_TTL_MS,
            )

            when(result[0]) {
                "HIT" -> {
                    cacheMetrics.incrementCouponCacheHit()
                    return mapper.readValue(result[1], CouponIssuePolicy::class.java)
                }
                "STALE_REFRESH" ->{
                    cacheMetrics.incrementCouponCacheHit()
                    backgroundExecutor.execute {
                        try{
                            fillCache(cacheKey,lockKey, token, loader)
                        }catch (e: Exception){
                            log.warn {"백그라운드 SWR 갱신 실패 (key=$cacheKey): ${e.message}"}
                        }
                    }
                    return mapper.readValue(result[1], CouponIssuePolicy::class.java)
                }
                "LOAD" -> return fillCache(cacheKey, cacheKey, lockKey, loader)
                "WAIT" -> Thread.sleep(WAIT_BACKOFF_MS) // 나머지 스레드는 모두 BACKOFF 시간동안 sleep 하고 repeat 구문에 의해 최대 50번 반복실행됨.
            }
        }
        throw IllegalStateException("쿠폰 캐시 채우기 timeout (key=$cacheKey)")
    }

    private fun fillCache(
        cacheKey: String,
        lockKey: String,
        token: String,
        loader: () -> CouponIssuePolicy
    ): CouponIssuePolicy {
         try { // LOAD를 result로 받았다는건 해당 스레드가 대표가되어 DB에 가서 데이터를 가져오는 스레드라는 것.
            cacheMetrics.incrementCouponDbRead()
            val response = loader()
            redis.opsForHash<String,String>().putAll(
                cacheKey,
                mapOf(
                    "value" to mapper.writeValueAsString(response),
                    "fetchedAtMs" to Instant.now().toEpochMilli().toString()
                )
            )

            redis.expire(cacheKey, Duration.ofMillis(cacheProperties.ttlMs))
            return response
        } finally { // LOAD를 result로 받은 스레드가 책임지고 데이터를 redis에 set 해두었으면, lock을 해제한다.
            redis.runForLong(
                releaseLockScript,
                listOf(lockKey),
                token,
            )
        }
    }

    @PreDestroy
    fun shutdown() {
        backgroundExecutor.shutdown()
    }

    private companion object {
        const val MAX_RETRIES = 50
        const val WAIT_BACKOFF_MS = 20L
        const val LOCK_TTL_MS = 3_000L
    }

}