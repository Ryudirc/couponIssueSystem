package com.travio.coupon.api

import com.travio.coupon.api.dto.ReconcileRunResponse
import com.travio.coupon.batch.Reconciler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/reconcile")
class AdminReconcileController(
    private val reconciler: Reconciler,
) {

    @PostMapping("/run")
    fun run(): ReconcileRunResponse =
        ReconcileRunResponse.from(reconciler.auditAll())

}