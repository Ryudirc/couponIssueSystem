package com.travio.coupon.infrastructure.messaging


import com.travio.coupon.persistence.CouponRepository
import com.travio.coupon.domain.Issuance
import com.travio.coupon.persistence.IssuanceRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class IssuanceTransactionalWriter(
    private val issuanceRepository: IssuanceRepository,
    private val couponRepository: CouponRepository,
) {

    @Transactional
    fun insertAndIncrement(event: IssuanceRequested) {
        // DLT 토픽 message 를 replay 할때 이미 DB에 있는지 검사 하고 넣기 -> 정합성 검증을 위해 한번 더 check
        if(isAlreadyApplied(event)) return

        issuanceRepository.save(
            Issuance(
                couponId = event.couponId,
                userId = event.userId,
                issuedAt = event.issuedAt,
                expiresAt = event.expiresAt,
            )
        )
        couponRepository.incrementIssuedQuantity(event.couponId)
    }


    fun isAlreadyApplied(event: IssuanceRequested) : Boolean =
        issuanceRepository.existsByUserIdAndCouponId(event.userId,event.couponId)

}