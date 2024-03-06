package com.intellij.tools.ide.metrics.collector.meters

import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import io.opentelemetry.sdk.metrics.data.HistogramData
import io.opentelemetry.sdk.metrics.data.HistogramPointData
import io.opentelemetry.sdk.metrics.data.MetricData

fun HistogramData.calculatePercentile(percentile: Double): Double {
  val boundaries: List<Double> = this.points.first().boundaries
  val counts: List<Long> = this.points.first().counts

  val rank = counts.sum() * percentile
  var runningTotal = 0L
  for ((index, count) in counts.withIndex()) {
    runningTotal += count
    if (runningTotal >= rank) {
      return boundaries[index]
    }
  }

  // In case the percentile is not found within the boundaries, return the maximum boundary.
  return boundaries.last()
}

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