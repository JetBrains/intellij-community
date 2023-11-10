// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.metrics.benchmark

import com.intellij.openapi.application.PathManager
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.tool.withRetry
import com.intellij.tools.ide.metrics.collector.metrics.*
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter
import com.intellij.tools.ide.metrics.collector.telemetry.getMetricsFromSpanAndChildren
import java.io.File
import kotlin.time.Duration.Companion.milliseconds


class MetricsExtractor(val telemetryJsonFile: File = PathManager.getLogDir().resolve("opentelemetry.json").toFile()) {
  fun waitTillMetricsExported(spanName: String): List<PerformanceMetrics.Metric> {
    val originalMetrics: List<PerformanceMetrics.Metric>? = withRetry(retries = 3, delayBetweenRetries = 300.milliseconds) {
      TelemetryManager.getInstance().forceFlushMetrics()
      extractOpenTelemetrySpanMetrics(spanName)
    }

    return requireNotNull(originalMetrics) { "Couldn't find metrics for '$spanName' in $telemetryJsonFile" }
  }

  private fun extractOpenTelemetrySpanMetrics(spanName: String): List<PerformanceMetrics.Metric> {
    val originalMetrics = getMetricsFromSpanAndChildren(telemetryJsonFile, SpanFilter.equals(spanName))

    val attempts = originalMetrics.filter { it.id.name.contains("Attempt", ignoreCase = true) }
    val medianValueOfAttempts: Long = attempts.medianValue()

    val attemptMeanMetric = "attempt.mean.ms".toPerformanceMetricDuration(attempts.map { it.value }.average().toLong())
    val attemptMedianMetric = "attempt.median.ms".toPerformanceMetricDuration(medianValueOfAttempts)
    val attemptModeMetric = "attempt.mode".toPerformanceMetricDuration(attempts.modeValue())
    val attemptRangeMetric = "attempt.range.ms".toPerformanceMetricDuration(attempts.rangeValue())
    val attemptSumMetric = "attempt.sum.ms".toPerformanceMetricDuration(attempts.sumOf { it.value })
    val attemptCountMetric = "attempt.count".toPerformanceMetricDuration(attempts.size.toLong())
    val attemptStandardDeviationMetric = "attempt.standard.deviation".toPerformanceMetricDuration(attempts.standardDeviationValue())

    val mainMetricValue: Long = originalMetrics.single { it.id.name == spanName }.value
    val totalTestDurationMetric = "total.test.duration.ms".toPerformanceMetricDuration(mainMetricValue)

    return listOf(totalTestDurationMetric, attemptMeanMetric, attemptMedianMetric,
                  attemptModeMetric, attemptRangeMetric, attemptSumMetric,
                  attemptCountMetric, attemptStandardDeviationMetric)
  }
}