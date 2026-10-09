package com.travio.coupon.infrastructure.messaging

import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class IssuanceWorker(
    private val writer: IssuanceWriter
) {

    @KafkaListener(
        topics = [IssuanceTopics.REQUESTED],
        groupId = IssuanceTopics.CONSUMER_GROUP, // 컨슈머 그룹에는 워커(정확히는 워커 스레드)가 들어있다.
        concurrency = "3", // 동시에 3개의 컨슈머(스레드)가 메세지를 읽어 소비한다.
    )
    fun consume(event: IssuanceRequested) {
        writer.write(event)
    }
}