package com.travio.coupon.infrastructure.messaging

import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

// 기존에 만들었던 이벤트를 처리하는 Worker와 동일하나, 스프링의 관행에 맞게 Handler 라는 이름으로 지어주었음.
@Component
class IssuanceEventHandler(
    private val issuanceWriter: IssuanceWriter,
) {

    @Async(ISSUANCE_TASK_EXECUTOR)
    @EventListener
    fun handle(event: IssuanceRequested) {
        issuanceWriter.write(event)
    }

}ㅊ