package com.travio.coupon.application

import com.travio.coupon.api.dto.CreateCouponRequest
import com.travio.coupon.api.dto.IssuanceResponse
import com.travio.coupon.domain.Coupon
import com.travio.coupon.domain.CouponRepository
import com.travio.coupon.domain.Issuance
import com.travio.coupon.domain.IssuanceRepository
import com.travio.coupon.domain.IssuanceStatus
import com.travio.coupon.support.AlreadyIssuedException
import com.travio.coupon.support.CouponNotFoundException
import com.travio.coupon.support.NotStartedException
import com.travio.coupon.support.SoldOutException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CouponService(
    private val couponRepository: CouponRepository,
    private val issuanceRepository: IssuanceRepository,
    private val couponIssuer: CouponIssuer
) {

    @Transactional // 하나의 Write 연산만 있긴하지만 관례적으로 씀.
    fun createCoupon(request: CreateCouponRequest): Coupon {

        val coupon = couponRepository.save(
            Coupon(
                name = request.name,
                totalQuantity = request.totalQuantity,
                validityDays = request.validityDays,
                startsAt = request.startsAt,
            )
        )

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

        if(coupon.isSoldOut()) {
            throw SoldOutException()
        }

        if(issuanceRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw AlreadyIssuedException()
        }

        //redis Lua 원자 연산 추가(통과된 것들만 update)
        couponIssuer.tryIssue(couponId)

        //coupon.issuedQuantity++
        // 메모리에 있던 issuedQuantity를 쓰면 두개의 스레드가 동시에 접근해서 메모리에 가진 값으로 update를 해버리기 때문에 갱신손실이 나타날 수 있어 DB에 있는 값을 기준으로 +1 하는 UPDATE
        couponRepository.incrementIssuedQuantity(couponId)

        return issuanceRepository.save(
            Issuance(
                userId = userId,
                couponId = couponId,
                issuedAt = now,
                expiresAt = now.plusDays(coupon.validityDays.toLong()),
            )
        )
    }

    fun fundByUser(userId: Long) : List<Issuance> =
        issuanceRepository.findByUserIdOrderByIssuedAtDesc(userId)


}