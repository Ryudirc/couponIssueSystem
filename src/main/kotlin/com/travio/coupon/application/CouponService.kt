package com.travio.coupon.application

import com.travio.coupon.api.dto.CreateCouponRequest
import com.travio.coupon.domain.Coupon
import com.travio.coupon.persistence.CouponRepository
import com.travio.coupon.domain.Issuance
import com.travio.coupon.infrastructure.messaging.IssuanceRequestProducer
import com.travio.coupon.infrastructure.messaging.IssuanceRequested
import com.travio.coupon.support.CouponNotFoundException
import com.travio.coupon.support.NotStartedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CouponService(
    private val couponRepository: CouponRepository,
    private val couponIssuer: CouponIssuer,
    private val issuanceRequestProducer: IssuanceRequestProducer,
) {

    @Transactional // 하나의 Write 연산만 있긴하지만 관례적으로 씀.
    fun createCoupon(request: CreateCouponRequest): Coupon {

        // 발매되는 쿠폰은 DB에 row 한줄임. 수량만 5천개로 지정.
        val coupon = couponRepository.save(
            Coupon(
                name = request.name,
                totalQuantity = request.totalQuantity,
                validityDays = request.validityDays,
                startsAt = request.startsAt,
            )
        )

        // Issuer가 왜 등장하게 되었는가?
        // redis 에서 쿠폰수량을 선차감 하기 위해, Issuer가 redis에 수량을 미리 init(redis에 set) 해둔다.
        couponIssuer.initStock(coupon.id!!, coupon.totalQuantity)

        return coupon
    }

    @Transactional
    fun issue(couponId: Long, userId: Long) : Issuance {

       val coupon = couponRepository.findById(couponId)
            .orElseThrow{ CouponNotFoundException() }

        val now = LocalDateTime.now()

        if(!coupon.isBookingOpen(now)) {
            throw NotStartedException()
        }

        //redis Lua 원자 연산 추가(통과된 것들만 update)
        couponIssuer.tryIssue(couponId,userId)

        val expiresAt = now.plusDays(coupon.validityDays.toLong())

        issuanceRequestProducer.publish(
            IssuanceRequested(
                couponId = couponId,
                userId = userId,
                issuedAt = now,
                expiresAt = expiresAt,
            )
        )

        return Issuance(
            userId = userId,
            couponId = couponId,
            issuedAt = now,
            expiresAt = expiresAt,
        )
    }
}