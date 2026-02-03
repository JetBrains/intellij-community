package com.intellij.tools.ide.metrics.collector.starter.metrics

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.starter.collector.StarterTelemetryJsonMeterCollector
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.diagnostic.dto.IndexingMetric
import com.intellij.util.indexing.diagnostic.dto.IndexingMetrics
import com.intellij.util.indexing.diagnostic.dto.getListOfIndexingMetrics
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name

fun IndexingMetrics.getListOfIndexingAndIdeMetrics(ideStartResult: IDEStartResult): List<PerformanceMetrics.Metric> {
  val indexingMetrics = getListOfIndexingMetrics().map {
    when (it) {
      is IndexingMetric.Duration -> PerformanceMetrics.newDuration(it.name, it.durationMillis)
      is IndexingMetric.Counter -> PerformanceMetrics.newCounter(it.name, it.value)
    }
  }
  return indexingMetrics +
         collectPerformanceMetricsFromCSV(ideStartResult, "lexer", "lexing") +
         collectPerformanceMetricsFromCSV(ideStartResult, "parser", "parsing")
}

private fun collectPerformanceMetricsFromCSV(
  runResult: IDEStartResult,
  metricPrefixInCSV: String,
  resultingMetricPrefix: String,
): List<PerformanceMetrics.Metric> {
  val timeRegex = Regex("${metricPrefixInCSV}\\.(.+)\\.time\\.ns")
  val time = StarterTelemetryJsonMeterCollector(MetricsSelectionStrategy.SUM) {
    it.name.startsWith("$metricPrefixInCSV.") && it.name.endsWith(".time.ns")
  }.collect(runResult.runContext) { name, value -> name to TimeUnit.NANOSECONDS.toMillis(value).toInt() }.associate {
    val language = timeRegex.find(it.id.name)?.groups?.get(1)?.value
    Pair(language, it.value)
  }
  val sizeRegex = Regex("${metricPrefixInCSV}\\.(.+)\\.size\\.bytes")
  val size = StarterTelemetryJsonMeterCollector(MetricsSelectionStrategy.SUM) {
    it.name.startsWith("$metricPrefixInCSV.") && it.name.endsWith(".size.bytes")
  }.collect(runResult.runContext).associate {
    val language = sizeRegex.find(it.id.name)?.groups?.get(1)?.value
    Pair(language, it.value)
  }
  val speed = time.filter { it.value != 0 }.mapValues {
    size.getValue(it.key) / it.value
  }

  return time.map { PerformanceMetrics.newDuration("${resultingMetricPrefix}Time#" + it.key, it.value) } +
         size.map { PerformanceMetrics.newCounter("${resultingMetricPrefix}Size#" + it.key, it.value) } +
         speed.map { PerformanceMetrics.newCounter("${resultingMetricPrefix}Speed#" + it.key, it.value) }
}

fun extractIndexingMetrics(startResult: IDEStartResult, projectName: String? = null): IndexingMetrics {
  val indexDiagnosticDirectory = startResult.runContext.logsDir / "indexing-diagnostic"
  val indexDiagnosticDirectoryChildren = Files.list(indexDiagnosticDirectory).filter { it.isDirectory() }.use { it.toList() }
  val projectIndexDiagnosticDirectory = indexDiagnosticDirectoryChildren.let { perProjectDirs ->
    if (projectName == null) {
      perProjectDirs.singleOrNull() ?: error("Only one project diagnostic dir is expected: ${perProjectDirs.joinToString()}")
    }
    else {
      perProjectDirs.find { it.name.startsWith("$projectName.") }
    }
  }
  val jsonIndexDiagnostics = Files.list(projectIndexDiagnosticDirectory)
    .use { stream -> stream.filter { it.extension == "json" }.toList() }
    .filter { Files.size(it) > 0L }
    .mapNotNull { IndexDiagnosticDumper.readJsonIndexingActivityDiagnostic(it) }
  return IndexingMetrics(jsonIndexDiagnostics)
}

