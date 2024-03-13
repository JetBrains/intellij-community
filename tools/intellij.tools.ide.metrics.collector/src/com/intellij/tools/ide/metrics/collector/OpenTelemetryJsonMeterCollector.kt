package com.intellij.tools.ide.metrics.collector

import com.intellij.platform.diagnostic.telemetry.exporters.meters.MetricsJsonImporter
import com.intellij.tools.ide.metrics.collector.meters.*
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.data.MetricDataType
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
    val metricsFiles = logsDirPath.listDirectoryEntries("*.json").filter { it.name.startsWith("open-telemetry-meter") }
    require(metricsFiles.isNotEmpty()) {
      "JSON files with metrics `open-telemetry-meters.***.json` must exist in directory '$logsDirPath'"
    }

    return metricsFiles
  }

  override fun collect(logsDirPath: Path): List<PerformanceMetrics.Metric> {
    val telemetryMetrics: List<MetricData> = getOpenTelemetryJsonReportFiles(logsDirPath).flatMap {
      MetricsJsonImporter.fromJsonFile(it)
    }
      .filter(meterFilter)

    val metricsGroupedByName: Map<String, List<MetricData>> = telemetryMetrics.groupBy { it.name }
    val selectedMetric: List<MetricData> = metricsGroupedByName.map { metricsSelectionStrategy.selectMetric(it.value, it.value.first().type) }

    return selectedMetric.flatMap {
      when (it.type) {
        MetricDataType.LONG_SUM -> LongCounterToMetricConverter()
        MetricDataType.DOUBLE_SUM -> DoubleCounterToMetricConverter()
        MetricDataType.LONG_GAUGE -> LongGaugeToMetricConverter()
        MetricDataType.DOUBLE_GAUGE -> DoubleGaugeToMetricConverter()
        MetricDataType.HISTOGRAM -> DoubleHistogramMeterToMetricConverter()
        else -> TODO("Type ${it.type} isn't supported yet")
      }.convert(it)
    }
  }
}