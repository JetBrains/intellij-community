package com.intellij.tools.ide.metrics.collector.metrics

fun findMetricValue(metrics: List<PerformanceMetrics.Metric>, metric: PerformanceMetrics.MetricId.Duration): Number = try {
  metrics.first { it.id.name == metric.name }.value
}
catch (e: NoSuchElementException) {
  throw NoSuchElementException("Metric with name '${metric.name}' wasn't found")
}
