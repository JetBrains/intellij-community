package com.intellij.tools.ide.metrics.collector

import com.intellij.platform.diagnostic.telemetry.exporters.meters.MetricsJsonImporter
import com.intellij.tools.ide.metrics.collector.meters.LongCounterToMetricConverter
import com.intellij.tools.ide.metrics.collector.meters.LongGaugeToMetricConverter
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import io.opentelemetry.sdk.metrics.data.MetricData
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name


/**
 * Extract meters from OpenTelemetry JSON report stored by [com.intellij.platform.diagnostic.telemetry.exporters.meters.TelemetryMeterJsonExporter]
 * [meterFilter] Input data: key - meter name. value - list of collected data points for that meter
 */
open class OpenTelemetryJsonMeterCollector(val metricsSelectionStrategy: MetricsSelectionStrategy,
                                           val meterFilter: (MetricData) -> Boolean) : TelemetryMetricsCollector {
  private fun getOpenTelemetryJsonReportFiles(logsDirPath: Path): List<Path> {
    val metricsFiles = logsDirPath.listDirectoryEntries("*.json").filter { it.name.startsWith("open-telemetry-metrics") }
    require(metricsFiles.isNotEmpty()) {
      "JSON files with metrics `open-telemetry-metrics.***.json` must exist in directory '$logsDirPath'"
    }

    return metricsFiles
  }


  override fun collect(logsDirPath: Path): List<PerformanceMetrics.Metric> {
    val telemetryMetrics: List<MetricData> = getOpenTelemetryJsonReportFiles(logsDirPath).flatMap {
      MetricsJsonImporter.fromJsonFile(it)
    }
      .filter(meterFilter)

    val metricsGroupedByName: Map<String, List<MetricData>> = telemetryMetrics.groupBy { it.name }
    val x: List<MetricData> = metricsGroupedByName.map { metricsSelectionStrategy.selectMetric(it.value) }

    // TODO: use meter converters for each metric data type
    //LongCounterToMetricConverter
    //LongGaugeToMetricConverter
    //DoubleHistogramMeterToMetricConverter

    return x.map { PerformanceMetrics.newDuration(it.name, -100500) }
  }
}