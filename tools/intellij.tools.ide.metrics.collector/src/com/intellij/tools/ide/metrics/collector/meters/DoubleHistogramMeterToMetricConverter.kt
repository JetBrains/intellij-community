package com.intellij.tools.ide.metrics.collector.meters

import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import io.opentelemetry.sdk.metrics.data.HistogramData
import io.opentelemetry.sdk.metrics.data.HistogramPointData
import io.opentelemetry.sdk.metrics.data.MetricData

/**
 * Calculate percentile for the histogram.
 *
 * Value: > 0 [percentile] < 100
 */
fun HistogramData.calculatePercentile(percentile: Byte): Double {
  assert(percentile in 0..100) { "Percentile must be between 0 and 100" }

  val point = this.points.first()

  val boundaries: List<Double> = point.boundaries
  val counts: List<Long> = point.counts

  assert(boundaries.isNotEmpty() && counts.isNotEmpty()) { "Boundaries and counts of histogram should not be empty" }

  val rank: Double = (counts.sum() * percentile) / 100.0
  var runningTotal = 0L
  for ((index, count) in counts.withIndex()) {
    runningTotal += count
    if (runningTotal >= rank) {
      // Ensure there is a next boundary
      return if (index < boundaries.size) boundaries[index]
      else boundaries.last()
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