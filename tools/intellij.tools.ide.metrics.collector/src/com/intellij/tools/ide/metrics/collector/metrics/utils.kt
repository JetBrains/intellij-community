package com.intellij.tools.ide.metrics.collector.metrics

import com.intellij.tools.ide.metrics.collector.collector.PerformanceMetrics

fun String.createPerformanceMetricCounter(): PerformanceMetrics.MetricId.Counter = PerformanceMetrics.MetricId.Counter(this)

fun String.createPerformanceMetricDuration(): PerformanceMetrics.MetricId.Duration = PerformanceMetrics.MetricId.Duration(this)

fun findMetricValue(metrics: List<PerformanceMetrics.Metric>, metric: PerformanceMetrics.MetricId.Duration): Number = try {
  metrics.first { it.id.name == metric.name }.value
}
catch (e: NoSuchElementException) {
  throw NoSuchElementException("Metric with name '${metric.name}' wasn't found")
}
