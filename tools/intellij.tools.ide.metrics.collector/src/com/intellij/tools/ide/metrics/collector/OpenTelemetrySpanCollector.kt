package com.intellij.tools.ide.metrics.collector

import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter
import java.nio.file.Path
import kotlin.io.path.isDirectory

/**
 * Collect spans from opentelemetry.json (usually located in the log directory) and convert it to metrics objects
 */
open class OpenTelemetrySpanCollector(val spanFilter: SpanFilter, private val spanAliases: Map<String, String> = mapOf()) : TelemetryMetricsCollector {
  override fun collect(logsDirPath: Path): List<PerformanceMetrics.Metric> {
    val telemetryJsonFile = if (logsDirPath.isDirectory()) {
      logsDirPath.resolve("opentelemetry.json").toAbsolutePath()
    }
    else logsDirPath

    return getMetricsFromSpanAndChildren(telemetryJsonFile, spanFilter, spanAliases = spanAliases)
  }
}