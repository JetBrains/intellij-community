package com.intellij.tools.ide.metrics.collector.meters

import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.EARLIEST
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.LATEST
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.MAXIMUM
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.MINIMUM
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.SUM
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableSumData
import io.opentelemetry.sdk.resources.Resource

class LongCounterMeterSelector : MetersSelector {
  override fun selectMetric(selectionType: MetricsSelectionStrategy, metrics: List<MetricData>): MetricData {
    return when (selectionType) {
      EARLIEST -> selectEarliestMeter(metrics)
      LATEST -> selectLatestMeter(metrics)
      MINIMUM -> metrics.minBy { it.longSumData.points.first().value }
      MAXIMUM -> metrics.maxBy { it.longSumData.points.first().value }
      SUM -> {
        val sum: LongPointData = SUM.selectMetric(metrics.flatMap { it.longSumData.points })
        val sumData = ImmutableSumData.create(metrics.first().longSumData.isMonotonic,
                                              metrics.first().longSumData.aggregationTemporality,
                                              listOf(sum))

        ImmutableMetricData.createLongSum(Resource.empty(), InstrumentationScopeInfo.empty(),
                                          metrics.first().name, metrics.first().description,
                                          metrics.first().unit, sumData)

      }
    }
  }
}