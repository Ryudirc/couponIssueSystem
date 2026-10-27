package com.travio.coupon.application

import com.travio.coupon.infrastructure.cache.CacheProperties
import com.travio.coupon.infrastructure.cache.CouponCacheRepository
import com.travio.coupon.persistence.CouponRepository
import com.travio.coupon.support.CouponNotFoundException
import org.springframework.stereotype.Service

@Service
class CouponIssuePolicyReader(
    private val couponRepository: CouponRepository,
    private val couponCacheRepository: CouponCacheRepository,
    private val cacheProperties: CacheProperties,
) {

    fun get(id: Long): CouponIssuePolicy =
        couponCacheRepository.getIssuePolicyOrLoad(id) {

            // DB에서 조회하고 응답하는 속도를 임의로 늦추기 위해 sleep을 걸어두었음.
            // 외부 네트워크 혹은 외부 리전에 있는 DB서버면 이게 필요없는데, 로컬 DB이다 보니 생각보다 속도가 빨라서 일부로 레이턴시를 줌.
            Thread.sleep(cacheProperties.simulatedLoadLatencyMs)

            couponRepository.findById(id)
                .orElseThrow{ CouponNotFoundException() }
                .let(CouponIssuePolicy::from)
        }

}