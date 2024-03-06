package com.intellij.tools.ide.metrics.collector.metrics

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableLongPointData

enum class MetricsSelectionStrategy {
  /** Collecyt the first by date metric reported in OpenTelemetry */
  EARLIEST,

  /** Collect the most recent metrics reported to OpenTelemetry. Useful used to collect gauges (that always report ever increasing value). */
  LATEST,

  /** Collect the metric with the minimum value reported. */
  MINIMUM,

  /** Collect the metric with the maximum value reported. */
  MAXIMUM,

  /** Sum up metrics. Useful to get cumulative metric from counters (that reports diff in telemetry). */
  SUM,

  /** Calculate average of the reported metrics. */
  AVERAGE;

  fun selectMetric(metrics: List<LongPointData>): LongPointData {
    return when (this) {
      EARLIEST -> metrics.minBy { it.startEpochNanos }
      LATEST -> metrics.maxBy { it.epochNanos }
      MINIMUM -> metrics.minBy { it.value }
      MAXIMUM -> metrics.maxBy { it.value }
      SUM -> ImmutableLongPointData.create(EARLIEST.selectMetric(metrics).startEpochNanos,
                                           LATEST.selectMetric(metrics).epochNanos,
                                           Attributes.empty(),
                                           metrics.sumOf { it.value })
      AVERAGE -> ImmutableLongPointData.create(EARLIEST.selectMetric(metrics).startEpochNanos,
                                               LATEST.selectMetric(metrics).epochNanos,
                                               Attributes.empty(),
                                               SUM.selectMetric(metrics).value / metrics.size)
    }
  }

  fun selectMetric(metrics: List<MetricData>): MetricData {
    return when (this) {
      EARLIEST -> metrics.minBy { it.data.points.minBy { point -> point.startEpochNanos }.startEpochNanos }
      LATEST -> metrics.maxBy { it.data.points.maxBy { point -> point.startEpochNanos }.startEpochNanos }
      MINIMUM -> TODO()
      MAXIMUM -> TODO()
      SUM -> TODO()
      AVERAGE -> TODO()
    }
  }
}