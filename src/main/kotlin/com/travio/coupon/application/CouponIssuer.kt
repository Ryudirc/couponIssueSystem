package com.travio.coupon.application

import com.travio.coupon.support.SoldOutException
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component

@Component
class CouponIssuer(
    private val redisTemplate: StringRedisTemplate
) {

    private val script: RedisScript<Long> = RedisScript.of(
        ClassPathResource("lua/issue.lua"),
        Long::class.java
    )

    fun tryIssue(couponId: Long) {
        val raw = redisTemplate.execute(script,
            listOf(stockKey(couponId))
        ) ?: error("Lua 스크립트 결과가 null")

        when(raw) {
            1L -> Unit
            0L -> throw SoldOutException()
            else -> error("예상치 못한 Lua 결과: $raw")
        }

    }

    fun initStock(couponId: Long, totalQuantity: Int) {
        // Key: coupon:$couponId:stock || Value: 쿠폰수량(totalQuantity)
        redisTemplate.opsForValue().set(stockKey(couponId), totalQuantity.toString())
    }


    private fun stockKey(couponId: Long) = "coupon:$couponId:stock"
}