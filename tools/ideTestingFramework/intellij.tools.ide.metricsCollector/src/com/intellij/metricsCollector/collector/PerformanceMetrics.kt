package com.intellij.metricsCollector.collector

import com.intellij.metricsCollector.publishing.ApplicationMetricDto
import com.intellij.openapi.util.BuildNumber
import java.time.OffsetDateTime

data class PerformanceMetrics(
  val buildNumber: BuildNumber,
  val generatedTime: OffsetDateTime,
  val projectName: String,
  val machineName: String,
  val branchName: String,
  val os: String,
  val metrics: List<Metric<*>>
) {
  sealed class MetricId<T : Number> {
    abstract val name: String

    /**
     * Metric used to measure duration of events in ms
     */
    data class Duration(override val name: String) : MetricId<Long>()

    /**
     * Metric used to count the number of times an event has occurred
     */
    data class Counter(override val name: String) : MetricId<Int>()
  }

  data class Metric<T : Number>(val id: MetricId<T>, val value: T)
}

fun PerformanceMetrics.Metric<*>.toJson() = ApplicationMetricDto(
  n = id.name,
  d = if (id is PerformanceMetrics.MetricId.Duration) (this.value as? Number)?.toLong() else null,
  c = if (id is PerformanceMetrics.MetricId.Counter) (this.value as? Number)?.toLong() else null
)