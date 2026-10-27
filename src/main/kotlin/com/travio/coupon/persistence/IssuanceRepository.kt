package com.travio.coupon.persistence

import com.travio.coupon.domain.Issuance

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface IssuanceRepository : JpaRepository<Issuance, Long> {
    fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean
    fun findByUserIdOrderByIssuedAtDesc(userId: Long) : List<Issuance>

    @Query("SELECT i.userId FROM Issuance i WHERE i.couponId = :couponId")
    fun findUserIdByCouponId(@Param("couponId") couponId: Long) : List<Long>
}