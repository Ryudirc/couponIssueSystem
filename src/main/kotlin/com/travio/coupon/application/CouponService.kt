package com.travio.coupon.application

import com.travio.coupon.api.dto.CreateCouponRequest
import com.travio.coupon.domain.Coupon
import com.travio.coupon.persistence.CouponRepository
import com.travio.coupon.domain.Issuance
import com.travio.coupon.infrastructure.cache.CacheProperties
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
    private val cacheProperties: CacheProperties,
    private val cacheMetrics: CacheMetrics,
    private val couponIssuer: CouponIssuer,
    private val issuanceRequestProducer: IssuanceRequestProducer,
) {
    /**
     * 쿠폰을 발행(create)하는 로직
     */
    @Transactional // 하나의 Write 연산만 있긴하지만 관례적으로 씀.
    fun createCoupon(request: CreateCouponRequest): Coupon {

        // 발매되는 쿠폰은 DB에 row 한줄임. (쿠폰의 내용이 동일하기 때문, 차감은 수량으로 하기때문에 상관없음.)
        val coupon = couponRepository.save(
            Coupon(
                name = request.name,
                totalQuantity = request.totalQuantity,
                validityDays = request.validityDays,
                startsAt = request.startsAt,
            )
        )

        // redis 에서 쿠폰수량을 선차감 하기 위해, Issuer가 redis에 수량을 미리 init(redis에 set) 해둔다.
        couponIssuer.initStock(coupon.id!!, coupon.totalQuantity)

        return coupon
    }

    /**
     * 발행된 쿠폰을 발급(issue)하는 로직
     */
    @Transactional
    fun issue(couponId: Long, userId: Long) : Issuance {
        cacheMetrics.incrementCouponDbRead() // 쿠폰 발급 전에 matrix에 기록

        // DB에서 조회하고 응답하는 속도를 임의로 늦추기 위해 sleep을 걸어두었음.
        // 외부 네트워크 혹은 외부 리전에 있는 DB서버면 이게 필요없는데, 로컬 DB이다 보니 생각보다 속도가 빨라서 일부로 레이턴시를 줌.
        Thread.sleep(cacheProperties.simulatedLoadLatencyMs)

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