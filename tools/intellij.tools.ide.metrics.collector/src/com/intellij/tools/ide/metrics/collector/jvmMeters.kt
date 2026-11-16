// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.metrics.collector

import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import java.nio.file.Path

/**
 * The meters of `JVMStatsToOTelReporter` that every process reports, with the strategy that reduces the readings of
 * each into one value.
 */
val JVM_METERS: Map<String, MetricsSelectionStrategy> = mapOf(
  "JVM.GC.collections" to MetricsSelectionStrategy.SUM,
  "JVM.GC.collectionTimesMs" to MetricsSelectionStrategy.SUM,
  "JVM.totalCpuTimeMs" to MetricsSelectionStrategy.SUM,
  "JVM.maxHeapBytes" to MetricsSelectionStrategy.MAXIMUM,
  "JVM.committedHeapBytes" to MetricsSelectionStrategy.LATEST,
  "JVM.maxThreadCount" to MetricsSelectionStrategy.MAXIMUM,
  "JVM.totalTimeToSafepointsMs" to MetricsSelectionStrategy.SUM,
  // total time the application was stopped at safepoints (the true stop-the-world time, distinct from time-to-safepoint above)
  "JVM.totalTimeAtSafepointsMs" to MetricsSelectionStrategy.SUM,
  "JVM.totalSafepointCount" to MetricsSelectionStrategy.SUM,
  // bytes allocated across all threads; the "Bytes" suffix makes it publish as JVM.totalMegabytesAllocated
  "JVM.totalBytesAllocated" to MetricsSelectionStrategy.SUM,
  "JVM.usedHeapBytes" to MetricsSelectionStrategy.MAXIMUM,
  "JVM.usedNativeBytes" to MetricsSelectionStrategy.MAXIMUM,
  "JVM.totalDirectByteBuffersBytes" to MetricsSelectionStrategy.MAXIMUM,
  "JVM.newThreadsCount" to MetricsSelectionStrategy.SUM,
  "OS.loadAverage" to MetricsSelectionStrategy.MAXIMUM,
)

/**
 * Metrics of the [meters] whose names start with a key, read from the meter files of [logsDir].
 *
 * A `...Bytes` meter becomes a `...Megabytes` metric, and a metric whose name contains `Time` is a duration.
 */
fun collectJvmMetrics(logsDir: Path, meters: Map<String, MetricsSelectionStrategy> = JVM_METERS): List<PerformanceMetrics.Metric> =
  meters.flatMap { (meterName, strategy) ->
    OpenTelemetryJsonMeterCollector(strategy) { it.name.startsWith(meterName) }
      .collect(logsDir) { name, value ->
        if (name.contains("Bytes")) name.replace("Bytes", "Megabytes") to (value / 1024 / 1024).toInt() else name to value.toInt()
      }
      .map {
        if (it.id.name.contains("Time")) PerformanceMetrics.newDuration(it.id.name, it.value)
        else PerformanceMetrics.newCounter(it.id.name, it.value)
      }
  }
