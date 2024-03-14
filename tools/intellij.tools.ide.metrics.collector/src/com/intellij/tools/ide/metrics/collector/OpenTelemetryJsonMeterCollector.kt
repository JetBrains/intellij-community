package com.intellij.tools.ide.metrics.collector

import com.intellij.platform.diagnostic.telemetry.exporters.meters.MetricsJsonImporter
import com.intellij.tools.ide.metrics.collector.meters.*
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.util.common.logError
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.metrics.data.Data
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.data.MetricDataType
import io.opentelemetry.sdk.resources.Resource
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name


/**
 * Extract meters from OpenTelemetry JSON report stored by [com.intellij.platform.diagnostic.telemetry.exporters.meters.TelemetryMeterJsonExporter]
 * [meterFilter] Input data: key - meter name. value - list of collected data points for that meter
 */
open class OpenTelemetryJsonMeterCollector(val metricsSelectionStrategy: MetricsSelectionStrategy,
                                           val meterFilter: (MetricData) -> Boolean) : TelemetryMetricsCollector {

  override fun collect(logsDirPath: Path): List<PerformanceMetrics.Metric> {
    val metricsFiles = logsDirPath.listDirectoryEntries("*.json").filter { it.name.startsWith("open-telemetry-meter") }

    // fallback to the collecting meters from the .csv files for older IDEs versions (where meters aren't exported to JSON files)
    if (metricsFiles.isEmpty()) {
      logError("Cannot find JSON files with metrics `open-telemetry-meters.***.json` in '$logsDirPath'. Falling back to use metrics from *.csv files")

      return OpenTelemetryCsvMeterCollector(metricsSelectionStrategy) { metricEntry ->
        val metricData = object : MetricData {
          override fun getResource(): Resource = TODO()
          override fun getInstrumentationScopeInfo(): InstrumentationScopeInfo = TODO()

          override fun getName(): String = metricEntry.key

          override fun getDescription(): String = TODO()
          override fun getUnit(): String = TODO()
          override fun getType(): MetricDataType = TODO()
          override fun getData(): Data<*> = TODO()
        }

        meterFilter(metricData)
      }.collect(logsDirPath)
    }

    val telemetryMetrics: List<MetricData> = metricsFiles.flatMap { MetricsJsonImporter.fromJsonFile(it) }
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