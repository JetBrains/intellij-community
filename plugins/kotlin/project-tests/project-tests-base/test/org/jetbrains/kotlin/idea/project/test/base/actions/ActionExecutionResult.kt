// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project.test.base.actions

import org.jetbrains.kotlin.idea.project.test.base.jvm.utils.JvmRuntimeUtils
import org.jetbrains.kotlin.idea.project.test.base.metrics.DefaultMetrics
import org.jetbrains.kotlin.idea.project.test.base.metrics.Metric
import org.jetbrains.kotlin.idea.project.test.base.metrics.MetricsCollector
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.system.measureTimeMillis

sealed class ActionExecutionResult {
    data class Success(val timeMs: Long) : ActionExecutionResult()

    data class Failure(val errors: List<ActionExecutionResultError>) : ActionExecutionResult() {
        constructor(error: ActionExecutionResultError) : this(listOf(error))
    }
}


@OptIn(ExperimentalContracts::class)
inline fun <R : Any> MetricsCollector.reportMetricForAction(metric: Metric, action: () -> R): R? {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }
    try {
        val result: R
        val beforeGcTime = JvmRuntimeUtils.getGCTime()
        val beforeJitTime = JvmRuntimeUtils.getJitTime()
        val timeMs = measureTimeMillis { result = action() }
        val afterJitTime = JvmRuntimeUtils.getJitTime()
        val afterGcTime = JvmRuntimeUtils.getGCTime()

        reportMetric(metric, timeMs)
        reportMetric(DefaultMetrics.gcTime, afterGcTime - beforeGcTime)
        reportMetric(DefaultMetrics.jitTime, afterJitTime - beforeJitTime)
        return result
    } catch (exception: Throwable) {
        reportFailure(ActionExecutionResultError.ExceptionDuringActionExecution(exception))
        return null
    }
}

fun ActionExecutionResult.withErrors(
    vararg errors: ActionExecutionResultError?
): ActionExecutionResult {
    val notNullErrors = errors.filterNotNull()
    if (notNullErrors.isNotEmpty()) return this
    return when (this) {
        is ActionExecutionResult.Failure -> ActionExecutionResult.Failure(this.errors + notNullErrors)
        is ActionExecutionResult.Success -> ActionExecutionResult.Failure(notNullErrors)
    }
}

sealed class ActionExecutionResultError {
    class ExceptionDuringActionExecution(val exception: Throwable) : ActionExecutionResultError()
    class InvalidActionExecutionResult(val error: String) : ActionExecutionResultError()
}

