package com.intellij.tools.ide.metrics.collector.meters

import com.intellij.tools.ide.metrics.collector.metrics.*
import io.opentelemetry.sdk.metrics.data.HistogramPointData
import io.opentelemetry.sdk.metrics.data.MetricData

class DoubleHistogramMeterToMetricConverter : MeterToMetricConverter {
  private fun MetricData.getMetricName(additionalMetricName: String, addUnitSuffix: Boolean = true): String {
    val unit: String = this.unit

    return this.name.removeSuffix(unit)
      .plus(if (addUnitSuffix) ".$additionalMetricName.$unit" else ".$additionalMetricName")
      .removeSuffix(".")
  }

  override fun convert(metricData: MetricData, transform: (String, Long) -> Pair<String, Int>): List<PerformanceMetrics.Metric> {
    val dataPoint: HistogramPointData = metricData.histogramData.points.first()

    val minMetric = PerformanceMetrics.newDuration(metricData.getMetricName("min"),
                                                   dataPoint.min.toInt())
    val maxMetric = PerformanceMetrics.newDuration(metricData.getMetricName("max"),
                                                   dataPoint.max.toInt())

    val measurementsCountMetric = PerformanceMetrics.newDuration(metricData.getMetricName("measurements.count", addUnitSuffix = false),
                                                                 dataPoint.count.toInt())

    val medianMetric = PerformanceMetrics.newDuration(metricData.getMetricName("median"),
                                                      metricData.histogramData.median().toInt())

    val stdevMetric = PerformanceMetrics.newDuration(metricData.getMetricName("standard.deviation"),
                                                     metricData.histogramData.standardDeviation().toInt())

    val pctl95 = PerformanceMetrics.newDuration(metricData.getMetricName("95.percentile"),
                                                metricData.histogramData.calculatePercentile(95).toInt())

    val pctl99 = PerformanceMetrics.newDuration(metricData.getMetricName("99.percentile"),
                                                metricData.histogramData.calculatePercentile(99).toInt())

    val madMetric = PerformanceMetrics.newDuration(metricData.getMetricName("mad"),
                                                   metricData.histogramData.mad().toInt())

    val rangeMetric = PerformanceMetrics.newDuration(metricData.getMetricName("range"),
                                                     metricData.histogramData.range().toInt())

    return listOf(minMetric, maxMetric, measurementsCountMetric, medianMetric, stdevMetric, pctl95, pctl99, madMetric, rangeMetric)
  }
}