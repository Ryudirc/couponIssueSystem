package com.travio.coupon.api

import com.travio.coupon.api.dto.IssuanceResponse
import com.travio.coupon.application.CouponService

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users/me/issuances")
class UserIssuanceController(
    private val issuanceService: CouponService,
) {

    @GetMapping
    fun listMine(@RequestHeader("X-User-Id") userId: Long,): List<IssuanceResponse> {
        return issuanceService.fundByUser(userId).map(IssuanceResponse::from)
    }
}