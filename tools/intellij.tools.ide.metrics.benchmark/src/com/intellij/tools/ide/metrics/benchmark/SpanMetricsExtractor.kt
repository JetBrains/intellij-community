// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.metrics.benchmark

import com.intellij.openapi.application.PathManager
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.tool.withRetryAsync
import com.intellij.tools.ide.metrics.collector.metrics.*
import com.intellij.tools.ide.metrics.collector.telemetry.OpentelemetrySpanJsonParser
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter
import java.nio.file.Path
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.milliseconds

class SpanMetricsExtractor(private val telemetryJsonFile: Path = getDefaultPathToTelemetrySpanJson()) {
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

    return requireNotNull(originalMetrics) { "Unable to extract metrics for '$spanName' from $telemetryJsonFile" }
  }

  private fun getAttemptsSpansStatisticalMetrics(attempts: List<PerformanceMetrics.Metric>, metricsPrefix: String): List<PerformanceMetrics.Metric> {
    val medianValueOfAttempts: Long = attempts.medianValue()
    val madValueOfAttempts = attempts.map { (it.value - medianValueOfAttempts).absoluteValue }.median()

    val attemptMeanMetric = PerformanceMetrics.newDuration("${metricsPrefix}attempt.mean.ms", attempts.map { it.value }.average().toLong())
    val attemptMedianMetric = PerformanceMetrics.newDuration("${metricsPrefix}attempt.median.ms", medianValueOfAttempts)

    // Why minimum matters? Its distribution is better than mean or median.
    // See https://blog.kevmod.com/2016/06/10/benchmarking-minimum-vs-average/
    val attemptMinMetric = PerformanceMetrics.newDuration("${metricsPrefix}attempt.min.ms", attempts.minOfOrNull { it.value }!!.toLong())
    val attemptRangeMetric = PerformanceMetrics.newDuration("${metricsPrefix}attempt.range.ms", attempts.rangeValue())
    val attemptSumMetric = PerformanceMetrics.newDuration("${metricsPrefix}attempt.sum.ms", attempts.sumOf { it.value })
    val attemptCountMetric = PerformanceMetrics.newCounter("${metricsPrefix}attempt.count", attempts.size.toLong())
    val attemptStandardDeviationMetric = PerformanceMetrics.newDuration("${metricsPrefix}attempt.standard.deviation",
                                                                        attempts.standardDeviationValue())
    // "... the MAD is a robust statistic, being more resilient to outliers in data set than the standard deviation."
    // See https://en.m.wikipedia.org/wiki/Median_absolute_deviation
    val attemptMadMetric = PerformanceMetrics.newDuration("${metricsPrefix}attempt.mad.ms", madValueOfAttempts)

    return listOf(attemptMeanMetric, attemptMedianMetric, attemptMinMetric,
                  attemptRangeMetric, attemptSumMetric, attemptCountMetric, attemptStandardDeviationMetric, attemptMadMetric)
  }

  /**
   * Author ot the perf test might want to report custom metrics from the test (span or meters)
   */
  private fun getAggregatedCustomSpansMetricsReportedFromTests(customMetrics: List<PerformanceMetrics.Metric>, metricsPrefix: String): List<PerformanceMetrics.Metric> {
    return customMetrics.groupBy { it.id.name }
      .map { group ->
        PerformanceMetrics.newDuration("${metricsPrefix}${group.key}", group.value.map { it.value }.average().toLong())
      }
  }

  private fun extractOpenTelemetrySpanMetrics(spanName: String, forWarmup: Boolean): List<PerformanceMetrics.Metric> {
    val originalMetrics = OpentelemetrySpanJsonParser(SpanFilter.any())
      .getSpanElements(telemetryJsonFile, spanElementFilter = { it.name == spanName && it.isWarmup == forWarmup })
      .map { PerformanceMetrics.newDuration(it.name, it.duration) }
      .toList()

    val attemptSuffix = "Attempt"
    var metricsPrefix = ""
    if (forWarmup) metricsPrefix = "warmup."

    val allAttempts = originalMetrics.filter { it.id.name.startsWith(attemptSuffix, ignoreCase = true) }
    val worstCount = allAttempts.size.toDouble().times(0.05).toInt()
    val attempts: List<PerformanceMetrics.Metric> = if (forWarmup) allAttempts else allAttempts.sortedBy { it.value }.dropLast(worstCount)

    // some tests might be forced to run without warmup attempts
    if (forWarmup && attempts.isEmpty()) return listOf()

    val attemptsStatisticalMetrics: List<PerformanceMetrics.Metric> = getAttemptsSpansStatisticalMetrics(attempts, metricsPrefix)

    val mainMetricValue: Long = originalMetrics.single { it.id.name == spanName }.value
    val totalTestDurationMetric = PerformanceMetrics.newDuration("${metricsPrefix}total.test.duration.ms", mainMetricValue)

    val customMetrics = originalMetrics.filterNot { it.id.name.startsWith(attemptSuffix, ignoreCase = true) || it.id.name == spanName }
    val aggregatedCustomMetrics = getAggregatedCustomSpansMetricsReportedFromTests(customMetrics, metricsPrefix)

    return attemptsStatisticalMetrics.plus(totalTestDurationMetric).plus(aggregatedCustomMetrics)
  }
}