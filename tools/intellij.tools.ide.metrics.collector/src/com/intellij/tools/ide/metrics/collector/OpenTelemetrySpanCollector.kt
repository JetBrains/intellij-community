package com.intellij.tools.ide.metrics.collector

import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter
import com.intellij.tools.ide.metrics.collector.telemetry.getMetricsFromSpanAndChildren
import java.nio.file.Path

/**
 * Collect spans from opentelemetry.json (usually located in the log directory) and convert it to metrics objects
 */
open class OpenTelemetrySpanCollector(val spanNames: List<String>, private val aliases: Map<String, String> = mapOf()) : TelemetryMetricsCollector {
  override fun collect(logsDirPath: Path): List<PerformanceMetrics.Metric> {

    val telemetryJsonFile = Path.of(System.getProperty("idea.diagnostic.opentelemetry.file",
                                                       logsDirPath.resolve("opentelemetry.json").toAbsolutePath().toString()))
    return getMetricsFromSpanAndChildren(telemetryJsonFile, SpanFilter.containsNameIn(spanNames), aliases = aliases)
  }
}