package com.intellij.tools.ide.metrics.collector

import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.Metric
import com.intellij.tools.ide.metrics.collector.telemetry.CombinedMetricsPostProcessor
import com.intellij.tools.ide.metrics.collector.telemetry.IDE_LIFETIME_TIMER_NAME
import com.intellij.tools.ide.metrics.collector.telemetry.MetricWithAttributes
import com.intellij.tools.ide.metrics.collector.telemetry.TOTAL_TEST_TIMER_NAME
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.jupiter.api.Test

class CombinedMetricsPostProcessorTest {

  @Test
  fun timerDurationsAreSkippedAndTimerAttributesAreKept() {
    val grouped = mapOf(
      TOTAL_TEST_TIMER_NAME to listOf(timer(TOTAL_TEST_TIMER_NAME, duration = 18_000, maxAwtDelay = 141)),
      IDE_LIFETIME_TIMER_NAME to listOf(timer(IDE_LIFETIME_TIMER_NAME, duration = 25_000, maxAwtDelay = 714)),
      "typing" to listOf(MetricWithAttributes(Metric.newDuration("typing", 350))),
    )

    val metrics = CombinedMetricsPostProcessor().process(grouped)

    metrics.shouldContainExactlyInAnyOrder(
      Metric.newDuration("$TOTAL_TEST_TIMER_NAME#max_awt_delay", 141),
      Metric.newDuration("$IDE_LIFETIME_TIMER_NAME#max_awt_delay", 714),
      Metric.newDuration("typing", 350),
    )
  }

  private fun timer(name: String, duration: Int, maxAwtDelay: Int): MetricWithAttributes {
    return MetricWithAttributes(Metric.newDuration(name, duration), mutableListOf(Metric.newDuration("max_awt_delay", maxAwtDelay)))
  }
}
