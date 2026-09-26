package com.intellij.tools.ide.metrics.collector.metrics

import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt


/** @see medianValue */
fun Iterable<Double>.median(): Double {
  val size = this.count()
  require(size > 0) { "Cannot calculate median value because collection is empty" }
  val sortedList = this.sorted()

  // odd collection size
  return if (size % 2 == 1) {
    sortedList[size / 2]
  }
  // even collection size
  else {
    (sortedList[size / 2 - 1] + sortedList[size / 2]) / 2
  }
}

/** @see medianValue */
fun Iterable<Int>.median(): Int = this.map { it.toDouble() }.median().toInt()

/** Returns median (NOT an average) value of a collection */
fun Iterable<PerformanceMetrics.Metric>.medianValue(): Int = this.map { it.value.toDouble() }.median().toInt()

// TODO: write unit tests
/**
 * Median absolute deviation
 *
 * "... the MAD is a robust statistic, being more resilient to outliers in a data set than the standard deviation."
 *
 * [Median absolute deviation](https://en.m.wikipedia.org/wiki/Median_absolute_deviation)
 */
fun Iterable<Double>.mad(): Double {
  val medianValue = this.median()
  return this.map { (it - medianValue).absoluteValue }.median()
}

/** @see mad() */
fun Iterable<Long>.mad(): Int = this.map { it.toDouble() }.mad().toInt()

/** @see mad() */
fun Iterable<PerformanceMetrics.Metric>.madValue(): Int = this.map { it.value.toDouble() }.mad().toInt()

/**
 * Calculates the standard deviation (std) - a measure of how spread out numbers are.
 * A low std indicates that the values tend to be close to the mean, while a high std indicates that the values are spread out over a wider range.
 *
 * [Standard Deviation](https://en.wikipedia.org/wiki/Standard_deviation)
 */
fun <T : Number> Iterable<T>.standardDeviation(): Int {
  val mean = this.map { it.toDouble() }.average()
  return sqrt(this.map { (it.toDouble() - mean).pow(2) }.average()).toInt()
}

/** @see standardDeviation */
fun Iterable<PerformanceMetrics.Metric>.standardDeviationValue(): Int = this.map { it.value }.standardDeviation()

/** @see rangeValue */
fun Iterable<Int>.range(): Int {
  val sorted = this.sorted()
  return sorted.last() - sorted.first()
}

/** Difference between the smallest and the largest values */
fun Iterable<PerformanceMetrics.Metric>.rangeValue(): Int = this.map { it.value }.range()

/**
 * Calculates [percentile] in the provided collection.
 * It will return only the existing data point (not a calculated "middle point" between data points)
 */
fun Iterable<Int>.percentile(percentile: Byte): Int {
  require(percentile in 0..100) { "Percentile must be between 0 and 100" }
  require(this.count() > 0) { "Cannot calculate percentile because collection is empty" }

  val sorted = this.sorted()
  return sorted[((percentile * (sorted.size - 1)) / 100.0).roundToInt().coerceIn(sorted.indices)]
}

/** @see percentile */
fun Iterable<PerformanceMetrics.Metric>.percentileValue(percentile: Byte): Int = this.map { it.value }.percentile(percentile)
