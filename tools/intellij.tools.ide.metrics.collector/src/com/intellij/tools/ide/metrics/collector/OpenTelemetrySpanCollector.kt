package com.intellij.tools.ide.metrics.collector

import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.Metric
import com.intellij.tools.ide.metrics.collector.telemetry.*
import java.nio.file.Path
import kotlin.io.path.isDirectory

/**
 * Collects spans from `opentelemetry.json` file usually located in the IDE's log directory and converts it to metrics objects.
 * More about [OpenTelemetry traces/spans](https://opentelemetry.io/docs/concepts/signals/traces/#spans)
 *
 * For meters (scalar data) use [com.intellij.tools.ide.metrics.collector.OpenTelemetryJsonMeterCollector]
 *
 * To report spans/traces from IDE take a look at usages of [com.intellij.platform.diagnostic.telemetry.TelemetryManager.getTracer]
 * and [com.intellij.platform.diagnostic.telemetry.TelemetryManager.Companion.getTracer]
 */
open class OpenTelemetrySpanCollector(val spanFilter: SpanFilter, private val spanAliases: Map<String, String> = mapOf()) : MetricsCollector {
  override fun collect(logsDirPath: Path): List<PerformanceMetrics.Metric> {
    val telemetryJsonFile = if (logsDirPath.isDirectory()) {
      logsDirPath.resolve("opentelemetry.json").toAbsolutePath()
    }
    else logsDirPath

    return getMetricsFromSpanAndChildren(telemetryJsonFile, spanFilter, spanAliases = spanAliases)
  }

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