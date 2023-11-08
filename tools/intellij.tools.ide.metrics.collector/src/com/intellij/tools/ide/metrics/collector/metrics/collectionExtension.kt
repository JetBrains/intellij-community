package com.intellij.tools.ide.metrics.collector.metrics

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

fun Iterable<PerformanceMetrics.Metric>.medianValue(): Long = this.map { it.value }.median()