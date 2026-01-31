package com.intellij.tools.ide.metrics.collector.metrics

import com.intellij.tools.ide.metrics.collector.meters.DoubleCounterMeterSelector
import com.intellij.tools.ide.metrics.collector.meters.DoubleGaugeMeterSelector
import com.intellij.tools.ide.metrics.collector.meters.DoubleHistogramMeterSelector
import com.intellij.tools.ide.metrics.collector.meters.LongCounterMeterSelector
import com.intellij.tools.ide.metrics.collector.meters.LongGaugeMeterSelector
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.data.MetricDataType
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
  SUM;

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
    }
  }

  fun selectMetric(metrics: List<MetricData>, metricType: MetricDataType): MetricData {
    val selector = when (metricType) {
      MetricDataType.LONG_SUM -> LongCounterMeterSelector()
      MetricDataType.DOUBLE_SUM -> DoubleCounterMeterSelector()
      MetricDataType.LONG_GAUGE -> LongGaugeMeterSelector()
      MetricDataType.DOUBLE_GAUGE -> DoubleGaugeMeterSelector()
      MetricDataType.HISTOGRAM -> DoubleHistogramMeterSelector()
      else -> TODO("$metricType meter selector isn't supported yet")
    }

    return selector.selectMetric(this, metrics)
  }
}