package com.travio.coupon.api.dto

import com.travio.coupon.domain.Coupon
import java.time.LocalDateTime

class CouponResponse(
    val id: Long,
    val name: String,
    val totalQuantity: Int,
    val issuedQuantity: Int,
    val validityDays: Int,
    val startsAt: LocalDateTime?,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(coupon: Coupon): CouponResponse
         = CouponResponse(
                id = coupon.id!!,
                name = coupon.name,
                totalQuantity = coupon.totalQuantity,
                issuedQuantity = coupon.issuedQuantity,
                validityDays = coupon.validityDays,
                startsAt = coupon.startsAt,
                createdAt = coupon.createdAt,
            )
    }
}