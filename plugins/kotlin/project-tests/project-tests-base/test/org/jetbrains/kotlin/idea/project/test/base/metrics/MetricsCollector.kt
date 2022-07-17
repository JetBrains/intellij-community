// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project.test.base.metrics

import org.jetbrains.kotlin.idea.project.test.base.actions.ActionExecutionResultError

class MetricsCollector(private val iteration: Int) {
    private val metrics = mutableMapOf<Metric, Long>()
    private val errors = mutableListOf<ActionExecutionResultError>()

    fun reportMetric(metric: Metric, valueMs: Long) {
        if (metric in metrics) {
            error("Metric ${metric.id} already reported")
        }
        metrics[metric] = valueMs
    }

    fun reportFailure(failure: ActionExecutionResultError) {
        errors += failure
    }

    fun getMetricsData(): MetricsData = MetricsData(iteration, metrics.toMap(), errors.toList())
}