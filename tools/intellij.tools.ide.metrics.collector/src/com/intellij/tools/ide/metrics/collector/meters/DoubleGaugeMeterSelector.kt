package com.intellij.tools.ide.metrics.collector.meters

import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.*
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.metrics.data.DoublePointData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableDoublePointData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableGaugeData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData
import io.opentelemetry.sdk.resources.Resource

class DoubleGaugeMeterSelector : MetersSelector {
  override fun selectMetric(selectionType: MetricsSelectionStrategy, metrics: List<MetricData>): MetricData {
    return when (selectionType) {
      EARLIEST -> selectEarliestMeter(metrics)
      LATEST -> selectLatestMeter(metrics)
      MINIMUM -> metrics.minBy { it.doubleGaugeData.points.first().value }
      MAXIMUM -> metrics.maxBy { it.doubleGaugeData.points.first().value }
      SUM -> {
        val sum: DoublePointData = ImmutableDoublePointData.create(
          selectMetric(EARLIEST, metrics).doubleGaugeData.points.first().startEpochNanos,
          selectMetric(LATEST, metrics).doubleGaugeData.points.first().epochNanos,
          Attributes.empty(),
          metrics.sumOf { it.doubleGaugeData.points.first().value }
        )

        val sumData = ImmutableGaugeData.create(listOf(sum))

        ImmutableMetricData.createDoubleGauge(Resource.empty(), InstrumentationScopeInfo.empty(),
                                              metrics.first().name, metrics.first().description,
                                              metrics.first().unit, sumData)

      }
    }
  }
}