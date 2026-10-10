package com.travio.coupon.api.dto

import com.travio.coupon.application.CacheMetricsSnapshot

class CacheMetricsResponse(
    val couponDbReads: Long,
    val couponCacheHits: Long,
    val soldOutRedisExists: Long, // redis 참조 했는지
    val soldOutFastPathHits: Long, // application L1 cache 참조했는지
) {
    companion object {
        fun from(snapshot: CacheMetricsSnapshot): CacheMetricsResponse = CacheMetricsResponse(
            couponDbReads = snapshot.couponDbReads,
            couponCacheHits = snapshot.couponCacheHits,
            soldOutRedisExists = snapshot.soldOutRedisExists,
            soldOutFastPathHits = snapshot.soldOutFastPathHits,
        )
    }
}