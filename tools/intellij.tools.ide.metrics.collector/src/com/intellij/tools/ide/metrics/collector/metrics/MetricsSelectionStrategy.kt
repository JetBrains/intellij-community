package com.intellij.tools.ide.metrics.collector.metrics

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.metrics.data.HistogramPointData
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.data.MetricDataType
import io.opentelemetry.sdk.metrics.internal.data.*
import io.opentelemetry.sdk.resources.Resource

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
      MINIMUM -> TODO("MINIMUM operation isn't implemented yet")
      MAXIMUM -> TODO("MAXIMUM operation isn't implemented yet")
      SUM -> {
        when (metrics.first().type) {
          MetricDataType.LONG_SUM -> {
            val sum: LongPointData = SUM.selectMetric(metrics.flatMap { it.longSumData.points })
            val sumData = ImmutableSumData.create(metrics.first().longSumData.isMonotonic,
                                                  metrics.first().longSumData.aggregationTemporality,
                                                  listOf(sum))

            ImmutableMetricData.createLongSum(Resource.empty(), InstrumentationScopeInfo.empty(),
                                              metrics.first().name, metrics.first().description,
                                              metrics.first().unit, sumData)
          }
          MetricDataType.HISTOGRAM -> {
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
          else -> TODO("SUM operation isn't supported yet for the type ${metrics.first().type}")
        }
      }
      AVERAGE -> TODO("AVERAGE operation isn't implemented yet")
    }
  }
}