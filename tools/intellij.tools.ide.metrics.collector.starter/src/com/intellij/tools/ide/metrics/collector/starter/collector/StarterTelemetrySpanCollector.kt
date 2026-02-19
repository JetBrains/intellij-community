package com.intellij.tools.ide.metrics.collector.starter.collector

import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.metrics.collector.OpenTelemetrySpanCollector
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter

/**
 * Collects OpenTelemetry spans as a Starter-specific adapter for [com.intellij.tools.ide.metrics.collector.OpenTelemetrySpanCollector]
 *
 * To publish collected metrics use [com.intellij.tools.ide.metrics.collector.starter.publishing.MetricsPublisher.Companion.newInstance]
 */
open class StarterTelemetrySpanCollector(
  spanFilter: SpanFilter,
  spanAliases: Map<String, String> = mapOf(),
) : OpenTelemetrySpanCollector(spanFilter, spanAliases), StarterMetricsCollector {
  override fun collect(runContext: IDERunContext): List<PerformanceMetrics.Metric> = collect(runContext.logsDir)
}