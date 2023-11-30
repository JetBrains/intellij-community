package com.intellij.tools.ide.metrics.collector.metrics

import kotlin.math.pow
import kotlin.math.sqrt

/** Returns median (NOT an average) value of a collection */
fun Iterable<Long>.median(): Long {
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

/** @see median */
fun Iterable<PerformanceMetrics.Metric>.medianValue(): Long = this.map { it.value }.median()

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


/** Difference between the smallest and the largest values */
fun Iterable<Long>.range(): Long {
  val sorted = this.sorted()
  return sorted.last() - sorted.first()
}

/** @see range */
fun Iterable<PerformanceMetrics.Metric>.rangeValue(): Long = this.map { it.value }.range()

