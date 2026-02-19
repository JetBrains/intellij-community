package com.intellij.tools.ide.metrics.collector.starter.metrics

import com.intellij.ide.starter.models.IDEStartResult
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
            "MEM.avgFileMappingsRamBytes" to MetricsSelectionStrategy.LATEST,
            "JVM.GC.collections" to MetricsSelectionStrategy.SUM,
            "JVM.GC.collectionTimesMs" to MetricsSelectionStrategy.SUM,
            "JVM.totalCpuTimeMs" to MetricsSelectionStrategy.SUM,
            "JVM.maxHeapBytes" to MetricsSelectionStrategy.MAXIMUM,
            "JVM.committedHeapBytes" to MetricsSelectionStrategy.LATEST,
            "JVM.maxThreadCount" to MetricsSelectionStrategy.MAXIMUM,
            "JVM.totalTimeToSafepointsMs" to MetricsSelectionStrategy.SUM),
  ): List<PerformanceMetrics.Metric> {
    try {
      return metricsStrategies.flatMap { (metricName, strategy) ->
        StarterTelemetryJsonMeterCollector(strategy) { it.name.startsWith(metricName) }.collect(startResult.runContext){ name, value ->
          if (name.contains("Bytes")){
            return@collect name.replace("Bytes", "Megabytes") to (value / 1024 / 1024).toInt()
          } else {
            name to value.toInt()
          }
        }.map {
          if (it.id.name.contains("Time")) {
            PerformanceMetrics.Metric.newDuration(it.id.name, it.value)
          }
          else {
            PerformanceMetrics.Metric.newCounter(it.id.name, it.value)
          }
        }
      }
    }
    catch (e: Exception) {
      logError("Collecting JVM metrics: ${e.message}")
    }
    return emptyList()
  }
}