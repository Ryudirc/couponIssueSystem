package com.travio.coupon.application

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.travio.coupon.infrastructure.cache.SoldOutProperties
import com.travio.coupon.infrastructure.cache.SoldOutRedisRepository
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class SoldOutState(
    private val soldOutRedisRepository: SoldOutRedisRepository,
    private val cacheMetrics: CacheMetrics,
    properties: SoldOutProperties,
) {
        /*
        * 만약 메모리 캐시에 couponId로 조회가 안되면 build가 호출됨.
        * build를 통해 redis에서 해당 key가 있으면 그 boolean 값을 저장하고 그 값을 리턴
        *  */
    private val cache: LoadingCache<Long, Boolean> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMillis(properties.fastPathTtlMs))
        .maximumSize(MAX_TRACKED_COUPONS)
        .build {couponId ->
            cacheMetrics.incrementSoldOutRedisExists()
            soldOutRedisRepository.isFlagged(couponId)
        }

    fun isSoldOut(couponId: Long) : Boolean {
        val soldOut = cache.get(couponId) ?: false

        if(soldOut) cacheMetrics.incrementSoldOutFastPathHit()

        return soldOut
    }

    private companion object {
        const val MAX_TRACKED_COUPONS = 1_000L
    }
}
