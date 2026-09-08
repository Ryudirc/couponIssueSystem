package com.travio.coupon.support

class CouponNotFoundException(message: String = "쿠폰 정보를 찾을 수 없습니다") : RuntimeException(message)

class NotStartedException(message: String = "발급이 아직 시작되지 않았습니다") : RuntimeException(message)

class SoldOutException(message: String = "쿠폰이 매진되었습니다") : RuntimeException(message)

class AlreadyIssuedException(message: String = "이미 발급된 쿠폰입니다") : RuntimeException(message)

class IssuanceNotFoundException(message: String = "발급 내역을 찾을 수 없습니다") : RuntimeException(message)

class AlreadyUsedException(message: String = "이미 사용된 쿠폰입니다") : RuntimeException(message)

class ExpiredException(message: String = "유효기간이 만료된 쿠폰입니다") : RuntimeException(message)