package com.intellij.tools.ide.metrics.collector.meters

import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import io.opentelemetry.sdk.metrics.data.MetricData

class LongGaugeToMetricConverter : MeterToMetricConverter {
  override fun convert(metricData: MetricData, transform: (String, Long) -> Pair<String, Int>): List<PerformanceMetrics.Metric> {
    val metric = transform(metricData.name, metricData.longGaugeData.points.first().value)
    return listOf(PerformanceMetrics.newDuration(metric.first, metric.second))
  }
}