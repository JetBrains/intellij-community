package com.intellij.tools.ide.metrics.collector.metrics

/** Returns median (not average) value of a collection */
fun Iterable<Long>.median(): Long {
  val size = this.count()
  require(size > 0) { "Cannot calculate median value because collection is empty" }

  return this.sorted()[size / 2]
}

fun Iterable<PerformanceMetrics.Metric>.medianValue(): Long = this.map { it.value }.median()