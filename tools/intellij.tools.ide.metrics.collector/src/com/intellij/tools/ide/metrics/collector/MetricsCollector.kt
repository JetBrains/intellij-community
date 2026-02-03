package com.intellij.tools.ide.metrics.collector

import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import java.nio.file.Path

/**
 * Collects IDE's metrics
 */
interface MetricsCollector {
  /**
   * Collect IDE metrics from [logsDirPath] - path to the IDE's log dir
   * @see [com.intellij.tools.ide.metrics.collector.starter.metrics.MetricsDiffCalculator]
   */
  fun collect(logsDirPath: Path): List<PerformanceMetrics.Metric>
}