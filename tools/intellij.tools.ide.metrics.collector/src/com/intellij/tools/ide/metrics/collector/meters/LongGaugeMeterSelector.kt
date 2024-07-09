package com.intellij.tools.ide.metrics.collector.meters

import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy.*
import io.opentelemetry.sdk.metrics.data.MetricData

class LongGaugeMeterSelector : MetersSelector {
  override fun selectMetric(selectionType: MetricsSelectionStrategy, metrics: List<MetricData>): MetricData {
    return when (selectionType) {
      EARLIEST -> selectEarliestMeter(metrics)
      LATEST -> selectLatestMeter(metrics)
      MINIMUM -> metrics.minBy { it.longGaugeData.points.first().value }
      MAXIMUM -> metrics.maxBy { it.longGaugeData.points.first().value }

      // there is no point to sum the same gauge
      SUM -> selectLatestMeter(metrics)
    }
  }
}