package com.travio.coupon.api

import com.travio.coupon.api.dto.CacheMetricsResponse
import com.travio.coupon.application.CacheMetrics
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/metrics/cache")
class CacheMetricController(
    private val cacheMetrics: CacheMetrics,

) {

    @GetMapping
    fun snapshot() : CacheMetricsResponse =
        CacheMetricsResponse.from(cacheMetrics.snapshot())

    @PostMapping("/reset")
    fun reset() {
        cacheMetrics.reset()
    }


}