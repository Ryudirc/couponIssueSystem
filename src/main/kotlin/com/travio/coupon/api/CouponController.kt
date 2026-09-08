package com.travio.coupon.api

import com.travio.coupon.api.dto.CouponResponse
import com.travio.coupon.api.dto.CreateCouponRequest
import com.travio.coupon.api.dto.IssuanceResponse
import com.travio.coupon.application.CouponService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/coupons")
class CouponController(
    private val couponService: CouponService,
) {

    /**
     * 쿠폰 생성 API
     */
    @PostMapping
    fun createCoupon(@RequestBody request: CreateCouponRequest): ResponseEntity<CouponResponse> {
        val createCoupon = couponService.createCoupon(request)
        return ResponseEntity.status(201).body(CouponResponse.from(createCoupon))
    }

    /**
     * 쿠폰 발급 API
     */
    @PostMapping("/{couponId}/issue")
    fun issue(@PathVariable couponId: Long,@RequestHeader("X-User-Id") userId: Long,): IssuanceResponse {
        val issuance = couponService.issue(couponId, userId)
        return IssuanceResponse.from(issuance)
    }


}