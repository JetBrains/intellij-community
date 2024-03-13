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
fun Iterable<Long>.median(): Long = this.map { it.toDouble() }.median().toLong()

/** Returns median (NOT an average) value of a collection */
fun Iterable<PerformanceMetrics.Metric>.medianValue(): Long = this.map { it.value.toDouble() }.median().toLong()

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
fun Iterable<Long>.mad(): Long = this.map { it.toDouble() }.mad().toLong()

/** @see mad() */
fun Iterable<PerformanceMetrics.Metric>.madValue(): Long = this.map { it.value.toDouble() }.mad().toLong()

/**
 * Calculates the standard deviation (std) - a measure of how spread out numbers are.
 * A low std indicates that the values tend to be close to the mean, while a high std indicates that the values are spread out over a wider range.
 *
 * [Standard Deviation](https://en.wikipedia.org/wiki/Standard_deviation)
 */
fun <T : Number> Iterable<T>.standardDeviation(): Long {
  val mean = this.map { it.toDouble() }.average()
  return sqrt(this.map { (it.toDouble() - mean).pow(2) }.average()).toLong()
}

/** @see standardDeviation */
fun Iterable<PerformanceMetrics.Metric>.standardDeviationValue(): Long = this.map { it.value }.standardDeviation()

/** @see rangeValue */
fun Iterable<Long>.range(): Long {
  val sorted = this.sorted()
  return sorted.last() - sorted.first()
}

/** Difference between the smallest and the largest values */
fun Iterable<PerformanceMetrics.Metric>.rangeValue(): Long = this.map { it.value }.range()

// TODO: write unit tests
/** Calculates [percentile] in the provided collection */
fun Iterable<Long>.percentile(percentile: Byte): Long {
  val sorted = this.sorted()
  return sorted[((percentile * sorted.size) / 100.0).roundToInt().coerceIn(sorted.indices)]
}
