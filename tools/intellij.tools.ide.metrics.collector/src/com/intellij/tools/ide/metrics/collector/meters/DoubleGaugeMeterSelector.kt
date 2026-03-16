package com.intellij.tools.ide.metrics.collector.meters

import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.EARLIEST
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.LATEST
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.MAXIMUM
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.MINIMUM
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.SUM
import io.opentelemetry.sdk.metrics.data.MetricData

class DoubleGaugeMeterSelector : MetersSelector {
  override fun selectMetric(selectionType: MetricsSelectionStrategy, metrics: List<MetricData>): MetricData {
    return when (selectionType) {
      EARLIEST -> selectEarliestMeter(metrics)
      LATEST -> selectLatestMeter(metrics)
      MINIMUM -> metrics.minBy { it.doubleGaugeData.points.first().value }
      MAXIMUM -> metrics.maxBy { it.doubleGaugeData.points.first().value }

      // there is no point to sum the same gauge
      SUM -> selectLatestMeter(metrics)
    }
  }
}