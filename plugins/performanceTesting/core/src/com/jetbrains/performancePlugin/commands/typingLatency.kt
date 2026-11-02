// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.internal.statistic.collectors.fus.LatencyRecord
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.actionSystem.LatencyListener
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.jetbrains.performancePlugin.PerformanceTestSpan
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.CoroutineScope

/**
 * Runs [block] in the [DelayTypeCommand.SPAN_NAME] span and records the typing latency of the editor input inside it: the
 * `latency#max`, `latency#p90` and `latency#mean_value` attributes of the span, when the latency listener got samples.
 */
internal suspend fun <R> withTypingLatencySpan(block: suspend CoroutineScope.(Span) -> R): R {
  val latencyRecord = LatencyRecord()
  val connection = ApplicationManager.getApplication().messageBus.connect()
  try {
    connection.subscribe(LatencyListener.TOPIC, LatencyListener { _, _, latencyMs -> latencyRecord.update(latencyMs.toInt()) })
    return PerformanceTestSpan.TRACER.spanBuilder(DelayTypeCommand.SPAN_NAME).setParent(PerformanceTestSpan.getContext())
      .useWithScope { span ->
        val result = block(span)
        if (!latencyRecord.samples.isEmpty) {
          span.setAttribute("latency#max", latencyRecord.maxLatency.toLong())
          span.setAttribute("latency#p90", latencyRecord.percentile(90).toLong())
          span.setAttribute("latency#mean_value", latencyRecord.averageLatency)
        }
        result
      }
  }
  finally {
    connection.disconnect()
  }
}
