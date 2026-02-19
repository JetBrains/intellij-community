package com.intellij.tools.ide.metrics.collector.meters

import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.EARLIEST
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.LATEST
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.MAXIMUM
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.MINIMUM
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.SUM
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.metrics.data.HistogramPointData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramPointData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData
import io.opentelemetry.sdk.resources.Resource

class DoubleHistogramMeterSelector : MetersSelector {
  override fun selectMetric(selectionType: MetricsSelectionStrategy, metrics: List<MetricData>): MetricData {
    return when (selectionType) {
      EARLIEST, LATEST, MINIMUM, MAXIMUM -> selectMetric(SUM, metrics)
      SUM -> {
        val points: List<HistogramPointData> = metrics.flatMap { it.histogramData.points }
        val mergedCounts: List<Long> = (0..<points.first().counts.size).map { index ->
          points.sumOf { it.counts[index] }
        }

        val merged = ImmutableHistogramPointData.create(points.minBy { it.startEpochNanos }.startEpochNanos,
                                                        points.maxBy { it.epochNanos }.epochNanos,
                                                        Attributes.empty(), points.sumOf { it.sum },
                                                        points.any { it.hasMin() }, points.minBy { it.min }.min,
                                                        points.any { it.hasMax() }, points.maxBy { it.max }.max,
                                                        points.first().boundaries, mergedCounts
        )
        val sumData = ImmutableHistogramData.create(metrics.first().histogramData.aggregationTemporality, listOf(merged))

        ImmutableMetricData.createDoubleHistogram(Resource.empty(), InstrumentationScopeInfo.empty(),
                                                  metrics.first().name, metrics.first().description,
                                                  metrics.first().unit, sumData)
      }
    }
  }
}