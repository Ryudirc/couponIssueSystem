package com.travio.coupon.application

import com.travio.coupon.domain.Issuance
import com.travio.coupon.domain.IssuanceRepository
import com.travio.coupon.domain.IssuanceStatus
import com.travio.coupon.support.AlreadyUsedException
import com.travio.coupon.support.ExpiredException
import com.travio.coupon.support.IssuanceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class IssuanceService(
    private val issuanceRepository: IssuanceRepository,
) {

    @Transactional
    fun use(issuanceId: Long, userId: Long) : Issuance {

        val issuance = issuanceRepository.findById(issuanceId)
            .orElseThrow{ IssuanceNotFoundException() }

        when(issuance.status) {
            IssuanceStatus.USED -> throw AlreadyUsedException()
            IssuanceStatus.EXPIRED -> throw ExpiredException()
            IssuanceStatus.ISSUED -> Unit
        }

        val now = LocalDateTime.now()

        if(issuance.isExpired(now)) {
            throw ExpiredException()
        }

        issuance.markUsed(now)
        return issuance

    }

}