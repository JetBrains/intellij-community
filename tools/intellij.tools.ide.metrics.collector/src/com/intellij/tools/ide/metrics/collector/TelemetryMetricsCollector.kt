package com.intellij.tools.ide.metrics.collector

import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.Metric
import com.intellij.tools.ide.metrics.collector.telemetry.*
import java.nio.file.Path

interface TelemetryMetricsCollector {
  /**
   * Collect IDE metrics.
   *
   * [logsDirPath] - path to the IDE's log dir
   * @see com.intellij.tools.ide.metrics.collector.MetricsDiffCalculator
   */
  fun collect(logsDirPath: Path): List<PerformanceMetrics.Metric>

  /**
   * Reports duration of `nameSpan` and all its children spans.
   * Replaces the names with an alias if one was passed.
   * Besides, all attributes are reported as counters.
   * If there are multiple values with the same name:
   * 1. They will be re-numbered `<value>_1`, `<value>_2`, etc. and the sum will be recorded as `<value>`.
   * 2. In the sum value, mean value and standard deviation of attribute value will be recorded
   * 2a. If attribute ends with `#max`, in a sum the max of max will be recorded
   * 3a. If attribute ends with `#mean_value`, the mean value of mean values will be recorded
   */
  fun getMetricsFromSpanAndChildren(
    file: Path,
    filter: SpanFilter,
    metricSpanProcessor: SpanProcessor<MetricWithAttributes> = MetricSpanProcessor(),
    spanAliases: Map<String, String> = mapOf(),
    metricsPostProcessor: MetricsPostProcessor = CombinedMetricsPostProcessor(),
  ): List<Metric> {
    val spanElements = OpentelemetrySpanJsonParser(filter).getSpanElements(file).map {
      val name = spanAliases.getOrDefault(it.name, it.name)
      if (name != it.name) {
        return@map it.copy(name = name)
      }
      return@map it
    }

    val spanToMetricMap: Map<String, List<MetricWithAttributes>> = spanElements.mapNotNull { metricSpanProcessor.process(it) }
      .groupBy { it.metric.id.name }
    return metricsPostProcessor.process(spanToMetricMap)
  }
}