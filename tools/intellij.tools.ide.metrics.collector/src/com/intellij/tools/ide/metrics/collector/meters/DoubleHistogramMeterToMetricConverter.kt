package com.intellij.tools.ide.metrics.collector.meters

import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import io.opentelemetry.sdk.metrics.data.HistogramData
import io.opentelemetry.sdk.metrics.data.HistogramPointData
import io.opentelemetry.sdk.metrics.data.MetricData

class DoubleHistogramMeterToMetricConverter : MeterToMetricConverter {
  override fun convert(metricData: MetricData): List<PerformanceMetrics.Metric> {
    val dataPoint: HistogramPointData = metricData.histogramData.points.first()

    val boundaries: List<Double> = dataPoint.boundaries
    val counts: List<Long> = dataPoint.counts

    TODO("Calculate percentile from boundaries and counts")

    val average: Double = dataPoint.sum / dataPoint.count
    val avgMetric = PerformanceMetrics.newDuration("${metricData.name}-average", average.toLong())

    val minMetric = PerformanceMetrics.newDuration("${metricData.name}-min", dataPoint.min.toLong())
    val maxMetric = PerformanceMetrics.newDuration("${metricData.name}-max", dataPoint.max.toLong())


    return listOf(avgMetric, minMetric, maxMetric)
  }
}