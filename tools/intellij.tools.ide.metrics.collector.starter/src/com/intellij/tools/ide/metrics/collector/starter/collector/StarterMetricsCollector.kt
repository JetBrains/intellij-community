package com.intellij.tools.ide.metrics.collector.starter.collector

import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics

/**
 * Starter-specific adaptation of [com.intellij.tools.ide.metrics.collector.MetricsCollector].
 *
 * Collector is consumed by [com.intellij.tools.ide.metrics.collector.starter.publishing.MetricsPublisher]
 */
interface StarterMetricsCollector {
  /**
   * Collect IDE metrics from provided [runContext].
   * A collection can be invoked multiple times during the test.
   *
   * For example:
   * - First collection
   * - Test does something
   * - Second collection
   * -
   * Calculation of diff between first and second collection via [com.intellij.tools.ide.metrics.collector.starter.metrics.MetricsDiffCalculator]
   */
  fun collect(runContext: IDERunContext): List<PerformanceMetrics.Metric>
}