package com.intellij.tools.ide.metrics.collector.starter.collector

import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.metrics.collector.OpenTelemetryJsonMeterCollector
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import io.opentelemetry.sdk.metrics.data.MetricData


/**
 * Collects OpenTelemetry meters as a Starter-specific adapter for [com.intellij.tools.ide.metrics.collector.OpenTelemetryJsonMeterCollector]
 *
 * To publish collected metrics use [com.intellij.tools.ide.metrics.collector.starter.publishing.MetricsPublisher.Companion.newInstance]
 */
open class StarterTelemetryJsonMeterCollector(
  metricsSelectionStrategy: MetricsSelectionStrategy,
  meterFilter: (MetricData) -> Boolean,
) : OpenTelemetryJsonMeterCollector(metricsSelectionStrategy, meterFilter), StarterMetricsCollector {
  override fun collect(runContext: IDERunContext): List<PerformanceMetrics.Metric> = collect(runContext.logsDir)
  fun collect(runContext: IDERunContext, transform: (String, Long) -> Pair<String, Int>): List<PerformanceMetrics.Metric> = collect(runContext.logsDir, transform)
}