package com.travio.coupon.infrastructure.messaging

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import kotlin.concurrent.thread



private val log = KotlinLogging.logger {}

@Component
class InMemoryIssuanceWorker(
    private val queue: InMemoryIssuanceQueue,
    private val writer: IssuanceWriter,

) {
    private lateinit var workerThead: Thread

    @PostConstruct
    fun start() {
        workerThead = thread(name = "issuance-worker", isDaemon = true) {
            // background 에서 돌아가는 daemon thread
            while (!Thread.currentThread().isInterrupted) {
                val event = try {
                    queue.poll() ?: continue
                }catch (e: InterruptedException) {
                    Thread.currentThread().interrupt() // 예외가 나면 인터럽트해서 종료
                    break
                }
                try {
                    writer.write(event)
                }catch (e: Exception) {
                    log.error { "Worker write 실패: couponId=${event.couponId}, userId=${event.userId}" }
                }
            }
            log.info { "issuance-worker 종료" }
        }
    }


    @PreDestroy
    fun stop() {
        if(::workerThead.isInitialized) {
            workerThead.interrupt()
        }
    }
}