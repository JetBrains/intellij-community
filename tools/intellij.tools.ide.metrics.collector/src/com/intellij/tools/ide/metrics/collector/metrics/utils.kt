package com.intellij.tools.ide.metrics.collector.metrics

fun findMetricValue(metrics: List<PerformanceMetrics.Metric>, metricName: String): Number = try {
  metrics.first { it.id.name == metricName }.value
}
catch (e: NoSuchElementException) {
  throw NoSuchElementException("Metric with name '$metricName' wasn't found")
}
