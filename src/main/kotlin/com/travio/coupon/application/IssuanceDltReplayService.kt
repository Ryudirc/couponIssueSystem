package com.travio.coupon.application

import com.travio.coupon.domain.IssuanceDltLog
import com.travio.coupon.domain.IssuanceDltStatus
import com.travio.coupon.infrastructure.messaging.IssuanceRequestProducer
import com.travio.coupon.infrastructure.messaging.IssuanceRequested
import com.travio.coupon.persistence.IssuanceDltLogRepository

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

@Service
class IssuanceDltReplayService(
    private val issuanceDltLogRepository: IssuanceDltLogRepository,
    private val issuanceRequestProducer: IssuanceRequestProducer,
) {

    private val jsonMapper = JsonMapper.builder().findAndAddModules().build()


    @Transactional
    fun replay(ids: List<Long>): Int {
        val selectedIds = ids.distinct()
        require(selectedIds.isNotEmpty()) { "At least one DLT log id is required" }
        require(selectedIds.size <= MAX_REPLAY_COUNT ) { "At most $MAX_REPLAY_COUNT DLT logs can be replayed" }

        val logs = issuanceDltLogRepository.findAllByIdForUpdate(selectedIds).sortedBy { it.receivedAt }
        check(logs.size == selectedIds.size) { "Some DLT logs were not found" }

        val events = logs.map {log ->
            check(log.status == IssuanceDltStatus.PENDING) {
                "Only pending DLT logs can be replayed: ${log.id}"
            }

            log.toEvent()
        }
        logs.zip(events).forEach { (log,event) ->
            issuanceRequestProducer.publishAndWait(event)
            log.status = IssuanceDltStatus.REPLAYED
        }

        return logs.size
    }

    private fun IssuanceDltLog.toEvent(): IssuanceRequested =
        jsonMapper.readValue(payload, IssuanceRequested::class.java)

    private companion object {
        const val MAX_REPLAY_COUNT = 100
    }

}