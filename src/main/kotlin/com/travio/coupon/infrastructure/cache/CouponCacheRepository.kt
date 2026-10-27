package com.travio.coupon.infrastructure.cache

import com.travio.coupon.application.CacheMetrics
import com.travio.coupon.application.CouponIssuePolicy
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.UUID

@Repository
class CouponCacheRepository(
    private val redis: StringRedisTemplate,
    private val mapper: ObjectMapper,
    private val cacheProperties: CacheProperties,
    private val cacheMetrics: CacheMetrics,
) {
    private val singleFlightScript = listLuaScript("lua/cache-single-flight.lua")
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

        repeat(MAX_RETRIES) { // 최대 반복횟수만큼 반복
            val result = redis.runForStrings(
                singleFlightScript,
                listOf(cacheKey,lockKey),
                token,LOCK_TTL_MS,
            )

            when(result[0]) {
                "HIT" -> {
                    cacheMetrics.incrementCouponCacheHit()
                    return mapper.readValue(result[1], CouponIssuePolicy::class.java)
                }
                "LOAD" -> return try { // LOAD를 result로 받았다는건 해당 스레드가 대표가되어 DB에 가서 데이터를 가져오는 스레드라는 것.
                    cacheMetrics.incrementCouponDbRead()
                    val response = loader()
                    val json = mapper.writeValueAsString(response)
                    redis.opsForValue().set(cacheKey, json,Duration.ofMillis(cacheProperties.ttlMs))
                    response
                }finally { // LOAD를 result로 받은 스레드가 책임지고 데이터를 redis에 set 해두었으면, lock을 해제한다.
                    redis.runForLong(
                        releaseLockScript,
                        listOf(lockKey),
                        token,
                    )
                }
                "WAIT" -> Thread.sleep(WAIT_BACKOFF_MS) // 나머지 스레드는 모두 BACKOFF 시간동안 sleep 하고 repeat 구문에 의해 최대 50번 반복실행됨.
            }
        }
        throw IllegalStateException("쿠폰 캐시 채우기 timeout (key=$cacheKey)")
    }

    private companion object {
        const val MAX_RETRIES = 50
        const val WAIT_BACKOFF_MS = 20L
        const val LOCK_TTL_MS = 3_000L
    }

}