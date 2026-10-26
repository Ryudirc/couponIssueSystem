package com.travio.coupon.batch

import com.travio.coupon.infrastructure.cache.CouponReconcileRedisRepository
import com.travio.coupon.infrastructure.cache.ReconcileProperties
import com.travio.coupon.persistence.CouponRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class Reconciler(
    private val couponRepository: CouponRepository,
    private val reconcileRedisRepository: CouponReconcileRedisRepository,
    private val reconcileProperties: ReconcileProperties,
    private val couponReconciler: CouponReconciler,
) {

    @Scheduled(fixedRateString = "\${coupon.reconcile.interval-ms}")
    fun scheduledRecent() {
        val cutOffMs = System.currentTimeMillis() - reconcileProperties.gracePeriodMs
        val fromMs = cutOffMs - reconcileProperties.intervalMs * 2
        val couponIds = reconcileRedisRepository.couponIdsIssuedBetween(fromMs,cutOffMs)

        reconcileCoupons(couponIds)
    }

    fun auditAll(): ReconcileReport = reconcileCoupons(couponRepository.findAll().mapNotNull { it.id })


    private fun reconcileCoupons(couponIds: List<Long>) : ReconcileReport {

        val outcomes = couponIds.map { couponId ->
            try{
                couponReconciler.reconcile(couponId)
            }catch (e: Exception){
                log.warn(e) { "reconcile 중 예외 | coupon=$couponId, 다음 회차에 재시도" }
                CouponReconcileOutCome(driftAlert = true)
            }
        }

        return ReconcileReport(
            autoFixed = outcomes.sumOf { it.autoFixed },
            driftAlerts = outcomes.count { it.driftAlert },
            redisDbDrift = outcomes.sumOf { it.redisDbDrift }
        )
    }


}