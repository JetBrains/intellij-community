package com.intellij.tools.ide.metrics.collector.starter.metrics

import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.util.common.intersectKeys
import com.intellij.tools.ide.util.common.symmetricDiffOfKeys
import kotlin.math.abs

/**
 * Calculates the difference between two sets of PerformanceMetrics.Metric objects.
 */
object MetricsDiffCalculator {
  /**
   * @return Creates a new list of metrics with name = metric name and value = diff between old and new value (old minus new)
   *
   * [useAbsoluteValue] If true, the absolute value of difference will be used. Eg: |2 - 3| = 1 (not -1)
   */
  fun calculateDiff(first: Iterable<PerformanceMetrics.Metric>,
                    second: Iterable<PerformanceMetrics.Metric>,
                    useAbsoluteValue: Boolean = true): List<PerformanceMetrics.Metric> {
    val firstMap: Map<String, PerformanceMetrics.Metric> = first.associateBy { it.id.name }
    val secondMap: Map<String, PerformanceMetrics.Metric> = second.associateBy { it.id.name }

    val intersectedKeys = firstMap.intersectKeys(secondMap).sorted()

    val diff: MutableList<PerformanceMetrics.Metric> = intersectedKeys.map { metricName ->
      val valueWithSign = secondMap.getValue(metricName).value - firstMap.getValue(metricName).value
      val value = if (useAbsoluteValue) abs(valueWithSign) else valueWithSign

      secondMap.getValue(metricName).copy(value = value)
    }.toMutableList()

    val symmetricNamesDiff: Set<String> = firstMap.symmetricDiffOfKeys(secondMap)
    symmetricNamesDiff.forEach { metricName ->
      firstMap[metricName]?.apply { diff.add(this) }
      secondMap[metricName]?.apply { diff.add(this) }
    }

    return diff
  }
}