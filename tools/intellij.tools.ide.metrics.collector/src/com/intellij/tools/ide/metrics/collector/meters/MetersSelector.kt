package com.intellij.tools.ide.metrics.collector.meters

import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import io.opentelemetry.sdk.metrics.data.MetricData

interface MetersSelector {
  fun selectMetric(selectionType: MetricsSelectionStrategy, metrics: List<MetricData>): MetricData

  fun selectEarliestMeter(metrics: List<MetricData>) = metrics.minBy { it.data.points.minBy { point -> point.startEpochNanos }.startEpochNanos }
  fun selectLatestMeter(metrics: List<MetricData>) = metrics.maxBy { it.data.points.maxBy { point -> point.startEpochNanos }.startEpochNanos }
}