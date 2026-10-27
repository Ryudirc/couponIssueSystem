package com.travio.coupon.batch

import com.travio.coupon.infrastructure.cache.CouponReconcileRedisRepository
import com.travio.coupon.infrastructure.cache.SoldOutProperties
import com.travio.coupon.persistence.CouponRepository
import com.travio.coupon.persistence.IssuanceRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import kotlin.math.abs

private val log = KotlinLogging.logger {}

@Component
class CouponReconciler(
    private val couponRepository: CouponRepository,
    private val issuanceRepository: IssuanceRepository,
    private val reconcileRedisRepository: CouponReconcileRedisRepository,
    private val soldOutProperties: SoldOutProperties,
) {

    fun reconcile(couponId: Long) : CouponReconcileOutCome {

        // 자동 보정 불가 Case

        val coupon = couponRepository.findById(couponId).orElse(null)
            ?: return alert(couponId, "쿠폰 없음")

        val stock = reconcileRedisRepository.stock(couponId)
            ?: return alert(couponId, "Redis stock 없음")

        val dbDrift = coupon.totalQuantity - (coupon.issuedQuantity + stock)
        val stockNegative = stock < 0
        if(stockNegative) log.warn { "재고 음수 감지 coupon=$couponId | stock=$stock" }
        if(dbDrift != 0L) log.warn { "DB 측 불일치 감지 coupon=$couponId | residual=$dbDrift (자동 보정 불가, 사람 확인)" }

        if(stockNegative || dbDrift != 0L) {
            return CouponReconcileOutCome(redisDbDrift = abs(dbDrift), driftAlert = true)
        }

        // 자동 보정 가능 Case
        var autoFixed = reconcileSoldOut(couponId,stock)
        val userDrift = coupon.totalQuantity - (reconcileRedisRepository.userCount(couponId) + stock)
        // 쿠폰 총 수량에서 해당쿠폰을 발급받은 유저수와 재고 수를 더한값을 뺐을 때 0보다 크다면 -> 어느 과정에서 누락이 생긴건가?
        // 총 수량이 100개고, 재고가 30개, 유저카운트가 50개면, 분명 발급은 70개가 되었는데 20개가 비어버린다는 뜻이겠구나..
        var driftAlert = false
        if(userDrift > 0) {
            val redisUserIds = reconcileRedisRepository.userIds(couponId).mapNotNull { it.toLongOrNull() }
            val missingUserIds = issuanceRepository.findUserIdByCouponId(couponId) - redisUserIds.toSet()

            if(missingUserIds.size.toLong() == userDrift) {
                reconcileRedisRepository.addUsers(couponId, missingUserIds)
                autoFixed++
                log.warn { "auto-fix: 사용자 목록 SADD ${missingUserIds.size}건 | coupon=$couponId" }
            }else{
                driftAlert = true
                log.warn { "Redis 사용자 목록을 안전하게 복구할 수 없음 coupon=$couponId | residual=$userDrift" }
            }

        }else if(userDrift < 0) {
            driftAlert = true
            log.warn { "Redis 사용자 목록 초과 감지 coupon=$couponId | residual=$userDrift"}
        }

        return CouponReconcileOutCome(autoFixed = autoFixed, driftAlert = driftAlert)
    }

    private fun alert(couponId: Long, reason: String) : CouponReconcileOutCome {
        // 이 로그를 모니터링 시스템이 받아서 연동된 슬랙으로 알람을 보낼수 있음.
        log.warn { "대상 확인 불가 coupon=$couponId | reason=$reason (수동 확인 필요)" }
        return CouponReconcileOutCome(driftAlert = true)
    }

    private fun reconcileSoldOut(couponId: Long, stock: Long): Int {

        val soldOut = reconcileRedisRepository.soldOutExists(couponId)

        return when {
            stock > 0 && soldOut -> { // 재고가 있는데 매진 플래그가 True 이면
                reconcileRedisRepository.deleteSoldOut(couponId)
                log.info {"auto-fix: 매진 플래그 해제 | coupon=$couponId"}
                1 // 성공처리인 1 리턴
            }
            stock == 0L && !soldOut -> { // 매진인데 매진 플래그가 False 이면
                reconcileRedisRepository.setSoldOut(couponId,soldOutProperties.ttlSeconds)
                log.info { "auto-fix: 매진 플래그 설정 | coupon=$couponId" }
                1 // 성공처리인 1 리턴
            }
            else -> 0 // 아니면 0 -> soldOut 관련해서는 정상이므로 고친게 없어서 0
        }

    }

}



class CouponReconcileOutCome(
    val autoFixed: Int = 0, // 자동보정
    val redisDbDrift: Long = 0, // Redis 불일치
    val driftAlert: Boolean = false, // 불일치 알람 필요여부
)