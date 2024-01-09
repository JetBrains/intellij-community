// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.metrics.benchmark

import com.intellij.openapi.application.PathManager
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.tool.withRetryAsync
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.metrics.medianValue
import com.intellij.tools.ide.metrics.collector.metrics.rangeValue
import com.intellij.tools.ide.metrics.collector.metrics.standardDeviationValue
import com.intellij.tools.ide.metrics.collector.telemetry.MetricSpanProcessor
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter
import com.intellij.tools.ide.metrics.collector.telemetry.getMetricsFromSpanAndChildren
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

class MetricsExtractor(private val telemetryJsonFile: Path = getDefaultPathToTelemetrySpanJson()) {
  companion object {
    fun getDefaultPathToTelemetrySpanJson(): Path {
      return Path.of(System.getProperty("idea.diagnostic.opentelemetry.file",
                                        PathManager.getLogDir().resolve("opentelemetry.json").toAbsolutePath().toString()))
    }
  }

  @Suppress("TestOnlyProblems")
  suspend fun waitTillMetricsExported(spanName: String): List<PerformanceMetrics.Metric> {
    val originalMetrics: List<PerformanceMetrics.Metric>? = withRetryAsync(retries = 10, delayBetweenRetries = 300.milliseconds) {
      TelemetryManager.getInstance().forceFlushMetrics()
      extractOpenTelemetrySpanMetrics(spanName, forWarmup = true).plus(extractOpenTelemetrySpanMetrics(spanName, forWarmup = false))
    }

    return requireNotNull(originalMetrics) { "Couldn't find metrics for '$spanName' in $telemetryJsonFile" }
  }

  private fun extractOpenTelemetrySpanMetrics(spanName: String, forWarmup: Boolean): List<PerformanceMetrics.Metric> {
    val originalMetrics = getMetricsFromSpanAndChildren(file = telemetryJsonFile,
                                                        filter = SpanFilter { it.name == spanName && it.isWarmup == forWarmup },
                                                        metricSpanProcessor = MetricSpanProcessor(ignoreWarmupSpan = !forWarmup))

    var metricsPrefix = ""
    if (forWarmup) metricsPrefix = "warmup."

    val attempts = originalMetrics.filter { it.id.name.contains("Attempt", ignoreCase = true) }

    // some tests might be forced to run without warmup attempts
    if (forWarmup && attempts.isEmpty()) return listOf()

    val medianValueOfAttempts: Long = attempts.medianValue()

    val attemptMeanMetric = PerformanceMetrics.newDuration("${metricsPrefix}attempt.mean.ms", attempts.map { it.value }.average().toLong())
    val attemptMedianMetric = PerformanceMetrics.newDuration("${metricsPrefix}attempt.median.ms", medianValueOfAttempts)
    val attemptRangeMetric = PerformanceMetrics.newDuration("${metricsPrefix}attempt.range.ms", attempts.rangeValue())
    val attemptSumMetric = PerformanceMetrics.newDuration("${metricsPrefix}attempt.sum.ms", attempts.sumOf { it.value })
    val attemptCountMetric = PerformanceMetrics.newDuration("${metricsPrefix}attempt.count", attempts.size.toLong())
    val attemptStandardDeviationMetric = PerformanceMetrics.newDuration("${metricsPrefix}attempt.standard.deviation",
                                                                        attempts.standardDeviationValue())

    val mainMetricValue: Long = originalMetrics.single { it.id.name == spanName }.value
    val totalTestDurationMetric = PerformanceMetrics.newDuration("${metricsPrefix}total.test.duration.ms", mainMetricValue)

    return listOf(totalTestDurationMetric, attemptMeanMetric, attemptMedianMetric,
                  attemptRangeMetric, attemptSumMetric, attemptCountMetric, attemptStandardDeviationMetric)
  }
}