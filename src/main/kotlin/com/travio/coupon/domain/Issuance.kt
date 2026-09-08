package com.travio.coupon.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "issuance",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_issuance_user_coupon",
            columnNames = ["user_id", "coupon_id"]
        ),
    ],
    indexes = [
        Index(name = "idx_issuance_status", columnList = "status"),
        Index(name = "idx_issuance_coupon", columnList = "coupon_id"),
    ]

)
class Issuance(

    @Column(nullable = false)
    var userId: Long,

    @Column(nullable = false)
    var couponId: Long,

    @Enumerated(EnumType.STRING) // 문자열로 들어가게 해야지.. 안그러면 Ordinal, 숫자로들어감
    @Column(length = 16)
    var status: IssuanceStatus = IssuanceStatus.ISSUED,

    @Column(nullable = false,updatable = false)
    var issuedAt: LocalDateTime,

    @Column(nullable = false)
    var expiresAt: LocalDateTime,


    var usedAt: LocalDateTime? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

) {

    fun isExpired(now: LocalDateTime) : Boolean = now.isAfter(expiresAt)
    fun markUsed(now: LocalDateTime) {
        status = IssuanceStatus.USED
        usedAt = now
    }
}