package com.intellij.tools.ide.metrics.collector.meters

import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.EARLIEST
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.LATEST
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.MAXIMUM
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.MINIMUM
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.SUM
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.metrics.data.DoublePointData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableDoublePointData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableSumData
import io.opentelemetry.sdk.resources.Resource

class DoubleCounterMeterSelector : MetersSelector {
  override fun selectMetric(selectionType: MetricsSelectionStrategy, metrics: List<MetricData>): MetricData {
    return when (selectionType) {
      EARLIEST -> selectEarliestMeter(metrics)
      LATEST -> selectLatestMeter(metrics)
      MINIMUM -> metrics.minBy { it.doubleSumData.points.first().value }
      MAXIMUM -> metrics.maxBy { it.doubleSumData.points.first().value }
      SUM -> {
        val sum: DoublePointData = ImmutableDoublePointData.create(
          selectMetric(EARLIEST, metrics).doubleSumData.points.first().startEpochNanos,
          selectMetric(LATEST, metrics).doubleSumData.points.first().epochNanos,
          Attributes.empty(),
          metrics.sumOf { it.doubleSumData.points.first().value }
        )

        val sumData = ImmutableSumData.create(metrics.first().doubleSumData.isMonotonic,
                                              metrics.first().doubleSumData.aggregationTemporality,
                                              listOf(sum))

        ImmutableMetricData.createDoubleSum(Resource.empty(), InstrumentationScopeInfo.empty(),
                                            metrics.first().name, metrics.first().description,
                                            metrics.first().unit, sumData)

      }
    }
  }
}