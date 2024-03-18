package com.intellij.tools.ide.metrics.collector

import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import java.nio.file.Path

interface TelemetryMetricsCollector {
  /**
   * Collect IDE metrics.
   *
   * [logsDirPath] - path to the IDE's log dir
   * @see com.intellij.tools.ide.metrics.collector.MetricsDiffCalculator
   */
  fun collect(logsDirPath: Path): List<PerformanceMetrics.Metric>
}