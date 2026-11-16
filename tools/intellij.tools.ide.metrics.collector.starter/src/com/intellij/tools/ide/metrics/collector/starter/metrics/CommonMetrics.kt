package com.intellij.tools.ide.metrics.collector.starter.metrics

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.tools.ide.metrics.collector.JVM_METERS
import com.intellij.tools.ide.metrics.collector.collectJvmMetrics
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.starter.collector.StarterTelemetryJsonMeterCollector
import com.intellij.tools.ide.util.common.logError

object CommonMetrics {
  private fun Number.convertNsToMs(): Int {
    return (this.toLong() / 1_000_000).toInt()
  }

  fun getAwtMetrics(startResult: IDEStartResult): List<PerformanceMetrics.Metric> {
    try {
      return StarterTelemetryJsonMeterCollector(MetricsSelectionStrategy.LATEST) {
        it.name == "AWTEventQueue.dispatchTimeTotalNS"
      }.collect(startResult.runContext.logsDir) { name, value -> name to value.convertNsToMs() }.map {
        PerformanceMetrics.Metric.newDuration("AWTEventQueue.dispatchTimeTotal", it.value)
      }
    }
    catch (e: Exception) {
      logError("Collecting AWT metrics: ${e.message}")
    }
    return emptyList()
  }

  fun getWriteActionMetrics(startResult: IDEStartResult): List<PerformanceMetrics.Metric> {
    val metricsToStrategy = mapOf("writeAction.count" to MetricsSelectionStrategy.SUM,
                                  "writeAction.wait.ms" to MetricsSelectionStrategy.SUM,
                                  "writeAction.max.wait.ms" to MetricsSelectionStrategy.MAXIMUM,
                                  "writeAction.median.wait.ms" to MetricsSelectionStrategy.LATEST)
    try {
      return metricsToStrategy.flatMap { (metricName, strategy) ->
        StarterTelemetryJsonMeterCollector(strategy) { it.name.startsWith(metricName) }
          .collect(startResult.runContext).map {
            PerformanceMetrics.Metric.newCounter(metricName, it.value)
          }
      }
    }
    catch (e: Exception) {
      logError("Collecting Write Action metrics: ${e.message}")
    }
    return emptyList()
  }

  fun getJvmMetrics(
    startResult: IDEStartResult,
    metricsStrategies: Map<String, MetricsSelectionStrategy>
    = mapOf("MEM.avgRamBytes" to MetricsSelectionStrategy.LATEST,
            "MEM.avgRamMinusFileMappingsBytes" to MetricsSelectionStrategy.LATEST,
            "MEM.avgRamPlusSwapMinusFileMappingsBytes" to MetricsSelectionStrategy.LATEST,
            "MEM.avgFileMappingsRamBytes" to MetricsSelectionStrategy.LATEST) + JVM_METERS,
  ): List<PerformanceMetrics.Metric> {
    try {
      return collectJvmMetrics(startResult.runContext.logsDir, metricsStrategies)
    }
    catch (e: Exception) {
      logError("Collecting JVM metrics: ${e.message}")
    }
    return emptyList()
  }
}