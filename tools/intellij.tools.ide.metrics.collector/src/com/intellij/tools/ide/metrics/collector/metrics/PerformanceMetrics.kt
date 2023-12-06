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
    data class Duration internal constructor(override val name: String) : MetricId()

    /**
     * Metric used to count the number of times an event has occurred
     */
    data class Counter internal constructor(override val name: String) : MetricId()
  }

  data class Metric private constructor(val id: MetricId,
                                        val value: Long,
                                        val compareSetting: CompareSetting = CompareSetting.notComparing) {
    companion object {
      /**
       * Creates instance of the Counter metric type.
       * @see com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.MetricId.Counter
       */
      fun newCounter(name: String, value: Long, compareSetting: CompareSetting = CompareSetting.notComparing): Metric {
        return Metric(id = MetricId.Counter(name), value = value, compareSetting)
      }

      /**
       * Creates instance of the Duration metric type.
       * @see com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.MetricId.Duration
       */
      fun newDuration(name: String, durationMillis: Long, compareSetting: CompareSetting = CompareSetting.notComparing): Metric {
        return Metric(id = MetricId.Duration(name), value = durationMillis, compareSetting)
      }
    }
  }

  companion object {
    /**
     * Shortcut for [com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.MetricId.Counter]
     */
    fun newCounter(name: String, value: Long, compareSetting: CompareSetting = CompareSetting.notComparing): Metric =
      Metric.newCounter(name, value, compareSetting)

    /**
     * Shortcut for [com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.MetricId.Duration]
     */
    fun newDuration(name: String, durationMillis: Long, compareSetting: CompareSetting = CompareSetting.notComparing): Metric =
      Metric.newDuration(name, durationMillis, compareSetting)
  }
}

fun PerformanceMetrics.Metric.toJson() = ApplicationMetricDto(
  n = id.name,
  d = if (id is PerformanceMetrics.MetricId.Duration) this.value else null,
  c = if (id is PerformanceMetrics.MetricId.Counter) this.value else null
)