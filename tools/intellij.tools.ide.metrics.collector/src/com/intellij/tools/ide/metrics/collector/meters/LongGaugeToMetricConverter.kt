package com.intellij.tools.ide.metrics.collector.meters

import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import io.opentelemetry.sdk.metrics.data.MetricData

class LongGaugeToMetricConverter : MeterToMetricConverter {
  override fun convert(metricData: MetricData): List<PerformanceMetrics.Metric> {
    return listOf(PerformanceMetrics.newDuration(metricData.name, metricData.longGaugeData.points.first().value))
  }
}