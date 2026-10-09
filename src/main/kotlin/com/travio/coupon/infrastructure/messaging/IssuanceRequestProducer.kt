package com.travio.coupon.infrastructure.messaging

import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class IssuanceRequestProducer(
    private val kafkaTemplate: KafkaTemplate<String,Any>
) {

    fun publish(event: IssuanceRequested) {
        // topic에 전달할 event의 key 값을 userId로 잡는 이유는 topic이 저장되는 partition 에 고르게 분할하기 위함.
        kafkaTemplate.send(IssuanceTopics.REQUESTED, event.userId.toString(), event)
    }
}