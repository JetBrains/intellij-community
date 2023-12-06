package com.intellij.tools.ide.metrics.collector.metrics

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableLongPointData

enum class MetricsSelectionStrategy {
  EARLIEST,

  /** Usually used to collect gauges */
  LATEST,

  MINIMUM,
  MAXIMUM,
  SUM,
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
}