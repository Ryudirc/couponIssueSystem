package com.travio.coupon.domain

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CouponRepository : JpaRepository<Coupon, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long) : Coupon?

    @Modifying
    @Query("update Coupon c set c.issuedQuantity = c.issuedQuantity + 1 where c.id = :id")
    fun incrementIssuedQuantity(@Param("id") id: Long) :Int

}