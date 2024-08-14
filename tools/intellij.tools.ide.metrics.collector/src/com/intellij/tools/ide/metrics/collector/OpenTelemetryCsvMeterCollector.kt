package com.intellij.tools.ide.metrics.collector

import com.intellij.platform.diagnostic.telemetry.MetricsImporterUtils
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import io.opentelemetry.sdk.metrics.data.LongPointData
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Extract meters from OpenTelemetry CSV report stored by [com.intellij.platform.diagnostic.telemetry.exporters.meters.CsvMetricsExporter]
 * [metersFilter] Input data: key - meter name. value - list of collected data points for that meter
 */
@Deprecated("Use com.intellij.tools.ide.metrics.collector.OpenTelemetryJsonMeterCollector")
open class OpenTelemetryCsvMeterCollector(
  val metricsSelectionStrategy: MetricsSelectionStrategy,
  val metersFilter: (Map.Entry<String, List<LongPointData>>) -> Boolean,
) : MetricsCollector {
  private fun getOpenTelemetryCsvReportFiles(logsDirPath: Path): List<Path> {
    val metricsCsvFiles = logsDirPath.listDirectoryEntries("*.csv").filter { it.name.startsWith("open-telemetry-metrics") }
    require(metricsCsvFiles.isNotEmpty()) {
      "CSV files with metrics `open-telemetry-metrics.***.csv` must exist in directory '$logsDirPath'"
    }

    return metricsCsvFiles
  }

  fun collect(logsDirPath: Path, transform: (String, Long) -> Pair<String, Int>): List<PerformanceMetrics.Metric> {
    val telemetryMetrics: Map<String, LongPointData> =
      MetricsImporterUtils.fromCsvFile(getOpenTelemetryCsvReportFiles(logsDirPath))
        .filter(metersFilter)
        .map { it.key to metricsSelectionStrategy.selectMetric(it.value) }.toMap()

    return telemetryMetrics.map { transform(it.key, it.value.value) }.map { PerformanceMetrics.newDuration(name = it.first, durationMillis = it.second) }
  }

  override fun collect(logsDirPath: Path): List<PerformanceMetrics.Metric> {
    return collect(logsDirPath) { name, value -> name to value.toInt() }
  }
}