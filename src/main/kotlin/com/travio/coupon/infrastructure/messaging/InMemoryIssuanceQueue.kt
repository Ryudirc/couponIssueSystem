package com.travio.coupon.infrastructure.messaging

import com.travio.coupon.support.QueueFullException
import org.springframework.stereotype.Component
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@Component
class InMemoryIssuanceQueue {
    private val queue = LinkedBlockingQueue<IssuanceRequested>(CAPACITY)

    fun enqueue(event: IssuanceRequested) {
        if(!queue.offer(event)) {
            throw QueueFullException()
        }
    }

    fun poll() : IssuanceRequested? = queue.poll(POLL_TIMEOUT, TimeUnit.MILLISECONDS)
    fun size() = queue.size

    companion object {
        private const val CAPACITY = 10_000
        private const val POLL_TIMEOUT = 100L
    }


}