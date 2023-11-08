package com.intellij.tools.ide.metrics.collector.metrics

import com.intellij.openapi.util.BuildNumber
import com.intellij.tools.ide.metrics.collector.analysis.CompareSetting
import com.intellij.tools.ide.metrics.collector.publishing.ApplicationMetricDto
import java.time.OffsetDateTime

data class PerformanceMetrics(
  val buildNumber: BuildNumber,
  val generatedTime: OffsetDateTime,
  val projectName: String,
  val machineName: String,
  val branchName: String,
  val os: String,
  val metrics: List<Metric>
) {
  sealed class MetricId {
    abstract val name: String

    /**
     * Metric used to measure duration of events in ms
     */
    data class Duration(override val name: String) : MetricId()

    /**
     * Metric used to count the number of times an event has occurred
     */
    data class Counter(override val name: String) : MetricId()
  }

  data class Metric(val id: MetricId, val value: Long, val compareSetting: CompareSetting = CompareSetting.notComparing)
}

fun PerformanceMetrics.Metric.toJson() = ApplicationMetricDto(
  n = id.name,
  d = if (id is PerformanceMetrics.MetricId.Duration) this.value else null,
  c = if (id is PerformanceMetrics.MetricId.Counter) this.value else null
)