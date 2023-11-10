package com.intellij.tools.ide.metrics.collector.metrics

import kotlin.math.pow
import kotlin.math.sqrt

/** Returns median (not average) value of a collection */
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

/** @see [com.intellij.tools.ide.metrics.collector.metrics.CollectionExtensionKt.median] */
fun Iterable<PerformanceMetrics.Metric>.medianValue(): Long = this.map { it.value }.median()

fun <T : Number> Iterable<T>.standardDeviation(): Long {
  val mean = this.map { it.toDouble() }.average()
  return sqrt(this.map { (it.toDouble() - mean).pow(2) }.average()).toLong()
}

fun Iterable<PerformanceMetrics.Metric>.standardDeviationValue(): Long = this.map { it.value }.standardDeviation()

/** Frequency of the value in the collection */
fun <T : Number> Iterable<T>.mode(): T {
  return this.groupingBy { it }.eachCount().maxBy { it.value }.key
}

/** @see [com.intellij.tools.ide.metrics.collector.metrics.CollectionExtensionKt.mode] */
fun Iterable<PerformanceMetrics.Metric>.modeValue(): Long = this.map { it.value }.mode()

/** Difference between the smallest and the largest values */
fun Iterable<Long>.range(): Long {
  val sorted = this.sorted()
  return sorted.last() - sorted.first()
}

/** @see [com.intellij.tools.ide.metrics.collector.metrics.CollectionExtensionKt.range] */
fun Iterable<PerformanceMetrics.Metric>.rangeValue(): Long = this.map { it.value }.range()

