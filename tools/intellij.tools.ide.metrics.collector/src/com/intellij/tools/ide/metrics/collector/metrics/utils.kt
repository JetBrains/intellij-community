package com.intellij.tools.ide.metrics.collector.metrics

import com.intellij.tools.ide.metrics.collector.collector.PerformanceMetrics

fun String.toCounterMetricId(): PerformanceMetrics.MetricId.Counter = PerformanceMetrics.MetricId.Counter(this)
fun String.toPerformanceMetricCounter(counter: Long): PerformanceMetrics.Metric =
  PerformanceMetrics.Metric(id = this.toCounterMetricId(), value = counter)

fun String.toDurationMetricId(): PerformanceMetrics.MetricId.Duration = PerformanceMetrics.MetricId.Duration(this)
fun String.toPerformanceMetricDuration(durationMs: Long): PerformanceMetrics.Metric =
  PerformanceMetrics.Metric(id = this.toDurationMetricId(), value = durationMs)

fun findMetricValue(metrics: List<PerformanceMetrics.Metric>, metric: PerformanceMetrics.MetricId.Duration): Number = try {
  metrics.first { it.id.name == metric.name }.value
}
catch (e: NoSuchElementException) {
  throw NoSuchElementException("Metric with name '${metric.name}' wasn't found")
}
