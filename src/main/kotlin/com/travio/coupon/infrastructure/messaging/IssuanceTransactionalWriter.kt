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
}