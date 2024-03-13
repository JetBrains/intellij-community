package com.intellij.tools.ide.metrics.collector.meters

import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import io.opentelemetry.sdk.metrics.data.MetricData

interface MeterToMetricConverter {
  fun convert(metricData: MetricData): List<PerformanceMetrics.Metric>
}