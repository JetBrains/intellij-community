package com.intellij.tools.ide.metrics.collector.meters

import com.intellij.tools.ide.metrics.collector.metrics.*
import io.opentelemetry.sdk.metrics.data.HistogramPointData
import io.opentelemetry.sdk.metrics.data.MetricData

class DoubleHistogramMeterToMetricConverter : MeterToMetricConverter {
  private fun MetricData.getMetricName(additionalMetricName: String): String {
    val unit: String = this.unit

    return this.name.removeSuffix(unit)
      .plus(".$additionalMetricName.$unit")
      .removeSuffix(".")
  }

  override fun convert(metricData: MetricData): List<PerformanceMetrics.Metric> {
    val dataPoint: HistogramPointData = metricData.histogramData.points.first()

    val minMetric = PerformanceMetrics.newDuration(metricData.getMetricName("min"),
                                                   dataPoint.min.toLong())
    val maxMetric = PerformanceMetrics.newDuration(metricData.getMetricName("max"),
                                                   dataPoint.max.toLong())

    val medianMetric = PerformanceMetrics.newDuration(metricData.getMetricName("median"),
                                                      metricData.histogramData.median().toLong())
    val stdevMetric = PerformanceMetrics.newDuration(metricData.getMetricName("standard.deviation"),
                                                     metricData.histogramData.standardDeviation().toLong())

    val pctl95 = PerformanceMetrics.newDuration(metricData.getMetricName("95.percentile"),
                                                metricData.histogramData.calculatePercentile(95).toLong())

    val pctl99 = PerformanceMetrics.newDuration(metricData.getMetricName("99.percentile"),
                                                metricData.histogramData.calculatePercentile(99).toLong())

    val madMetric = PerformanceMetrics.newDuration(metricData.getMetricName("mad"),
                                                   metricData.histogramData.mad().toLong())

    val rangeMetric = PerformanceMetrics.newDuration(metricData.getMetricName("range"),
                                                     metricData.histogramData.range().toLong())

    return listOf(minMetric, maxMetric, medianMetric, stdevMetric, pctl95, pctl99, madMetric, rangeMetric)
  }
}