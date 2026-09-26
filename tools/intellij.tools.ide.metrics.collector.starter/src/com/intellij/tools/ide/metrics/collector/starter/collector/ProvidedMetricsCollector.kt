package com.intellij.tools.ide.metrics.collector.starter.collector

import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics

/** Just returns the provided metrics */
class ProvidedMetricsCollector(val metrics: List<PerformanceMetrics.Metric>) : StarterMetricsCollector {
  override fun collect(runContext: IDERunContext): List<PerformanceMetrics.Metric> = metrics
}