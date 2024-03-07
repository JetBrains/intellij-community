package com.intellij.tools.ide.metrics.collector.metrics

import io.opentelemetry.sdk.metrics.data.HistogramData

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

fun HistogramData.median(): Double {
  val frequencies = mutableListOf<Double>()
  this.points.forEach { point ->
    point.boundaries.zip(point.counts) { boundary, count ->
      repeat(count.toInt()) { frequencies.add(boundary) }
    }
  }
  return frequencies.median()
}

fun HistogramData.standardDeviation(): Double {
  val frequencies = mutableListOf<Double>()
  this.points.forEach { point ->
    point.boundaries.zip(point.counts) { boundary, count ->
      repeat(count.toInt()) { frequencies.add(boundary) }
    }
  }
  return frequencies.standardDeviation().toDouble()
}

/**
 * Median Absolute Deviation (MAD)
 * @see mad(java.lang.Iterable<java.lang.Double>)
 **/
fun HistogramData.mad(): Double {
  val frequencies = mutableListOf<Double>()
  this.points.forEach { point ->
    point.boundaries.zip(point.counts) { boundary, count ->
      repeat(count.toInt()) { frequencies.add(boundary) }
    }
  }
  return frequencies.map { it }.mad()
}

fun HistogramData.range(): Double {
  val boundaries = this.points.flatMap { it.boundaries }
  return boundaries.maxOrNull()!! - boundaries.minOrNull()!!
}
