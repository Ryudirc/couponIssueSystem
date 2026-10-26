package com.travio.coupon.infrastructure.cache

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

@Repository
class CouponReconcileRedisRepository(
    private val redisTemplate: StringRedisTemplate,
) {
    fun couponIdsIssuedBetween(fromExclusiveMs: Long,toInclusiveMs: Long) : List<Long> {
        if(toInclusiveMs <= fromExclusiveMs) return emptyList()

        val members = redisTemplate.opsForZSet().rangeByScore(
            "coupon:reconcile:recent",
            (fromExclusiveMs + 1).toDouble(),
            toInclusiveMs.toDouble(),
        ) ?: emptySet()

        return members.mapNotNull { it.toLongOrNull() }
    }


    fun stock(couponId: Long) : Long? =
        redisTemplate.opsForValue().get("coupon:$couponId:stock")?.toLongOrNull()

    fun userCount(couponId: Long) : Long =
        redisTemplate.opsForValue().size("coupon:$couponId:users") ?: 0L

    fun userIds(couponId: Long): Set<String> =
        redisTemplate.opsForSet().members("coupon:$couponId:users") ?: emptySet()

    fun soldOutExists(couponId: Long): Boolean =
        redisTemplate.hasKey("coupon:$couponId:sold_out")

    fun addUsers(couponId: Long, userIds: Collection<Long>) {
        if(userIds.isEmpty()) return
        redisTemplate.opsForSet().add("coupon:$couponId:users", *userIds.map{ it.toString() }.toTypedArray())
    }

    fun deleteSoldOut(couponId: Long) {
        redisTemplate.delete("coupon:$couponId:sold_out")
    }

    fun setSoldOut(couponId: Long, ttlSecond: Long) {
        redisTemplate.opsForValue().set("coupon:$couponId:sold_out", "1",ttlSecond,TimeUnit.SECONDS)
    }


}