package com.intellij.tools.ide.metrics.collector.meters

import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.*
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableGaugeData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableLongPointData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData
import io.opentelemetry.sdk.resources.Resource

class LongGaugeMeterSelector : MetersSelector {
  override fun selectMetric(selectionType: MetricsSelectionStrategy, metrics: List<MetricData>): MetricData {
    return when (selectionType) {
      EARLIEST -> selectEarliestMeter(metrics)
      LATEST -> selectLatestMeter(metrics)
      MINIMUM -> metrics.minBy { it.longGaugeData.points.first().value }
      MAXIMUM -> metrics.maxBy { it.longGaugeData.points.first().value }
      SUM -> {
        val sum: LongPointData = ImmutableLongPointData.create(
          selectMetric(EARLIEST, metrics).longGaugeData.points.first().startEpochNanos,
          selectMetric(LATEST, metrics).longGaugeData.points.first().epochNanos,
          Attributes.empty(),
          metrics.sumOf { it.longGaugeData.points.first().value }
        )

        val sumData = ImmutableGaugeData.create(listOf(sum))

        ImmutableMetricData.createLongGauge(Resource.empty(), InstrumentationScopeInfo.empty(),
                                            metrics.first().name, metrics.first().description,
                                            metrics.first().unit, sumData)

      }
    }
  }
}