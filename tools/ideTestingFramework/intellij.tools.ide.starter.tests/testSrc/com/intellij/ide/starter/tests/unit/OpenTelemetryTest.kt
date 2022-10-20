package com.intellij.ide.starter.tests.unit

import com.intellij.metricsCollector.collector.PerformanceMetrics.Metric
import com.intellij.metricsCollector.collector.PerformanceMetrics.MetricId.Counter
import com.intellij.metricsCollector.collector.PerformanceMetrics.MetricId.Duration
import com.intellij.metricsCollector.metrics.getMetrics
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.io.path.div

class OpenTelemetryTest {

  private val openTelemetryReports by lazy {
    Paths.get(this::class.java.classLoader.getResource("opentelemetry")!!.toURI())
  }

  @Test
  fun metricsCorrectlyCollected() {
    val metrics = getMetrics((openTelemetryReports / "opentelemetry.json").toFile(), "%findUsages")
    metrics.shouldContainExactlyInAnyOrder(listOf(
      Metric(Duration("%findUsages_1"), 531),
      Metric(Duration("%findUsages_2"), 4110),
      Metric(Duration("%findUsages_3"), 3090),
      Metric(Duration("%findUsages"), 7731),
      Metric(Duration("%findUsages#mean_value"), 2577),
      Metric(Duration("%findUsages#standard_deviation"), 1505),
      Metric(Duration("FindUsagesManager.startProcessUsages_1"), 510),
      Metric(Counter("FindUsagesManager.startProcessUsages_1#number_of_found_usages"), 1),
      Metric(Duration("FindUsagesManager.startProcessUsages_2"), 4107),
      Metric(Counter("FindUsagesManager.startProcessUsages_2#number_of_found_usages"), 549),
      Metric(Duration("FindUsagesManager.startProcessUsages_3"), 3088),
      Metric(Counter("FindUsagesManager.startProcessUsages_3#number_of_found_usages"), 844),
      Metric(Duration("FindUsagesManager.startProcessUsages"), 7705),
      Metric(Duration("FindUsagesManager.startProcessUsages#mean_value"), 2568),
      Metric(Duration("FindUsagesManager.startProcessUsages#standard_deviation"), 1513),
      Metric(Duration("FindUsagesManager.startProcessUsages#number_of_found_usages#mean_value"), 464),
      Metric(Duration("FindUsagesManager.startProcessUsages#number_of_found_usages#standard_deviation"), 349),
    ))
  }

  @Test
  fun metricsWithSingleSpan() {
    val metrics = getMetrics((openTelemetryReports / "opentelemetry_with_main_timer.json").toFile(), "performance_test")
    metrics.shouldContainExactlyInAnyOrder(listOf(
      Metric(Duration("delayType"), 3739),
      Metric(Counter("test#max_awt_delay"), 141),
      Metric(Counter("test#average_awt_delay"), 8),
    ))
  }

  @Test
  fun metricsCorrectlyCollected2() {
    val metrics = getMetrics((openTelemetryReports / "opentelemetry2.json").toFile(), "performance_test")
    metrics.shouldContainExactlyInAnyOrder(listOf(
      Metric(Duration("timer_1"), 1184),
      Metric(Counter("timer_1#average_awt_delay"), 3),
      Metric(Counter("timer_1#max_awt_delay"), 57),
      Metric(Duration("timer_2"), 1519),
      Metric(Counter("timer_2#average_awt_delay"), 7),
      Metric(Counter("timer_2#max_awt_delay"), 84),
      Metric(Duration("timer"), 2703),
      Metric(Duration("timer#mean_value"), 1351),
      Metric(Duration("timer#standard_deviation"), 167),
      Metric(Duration("findUsages_1"), 1205),
      Metric(Duration("findUsages_2"), 1184),
      Metric(Counter("findUsages_2#number_of_found_usages"), 1384),
      Metric(Duration("findUsages#standard_deviation"), 10),
      Metric(Duration("findUsages"), 2389),
      Metric(Duration("findUsages#mean_value"), 1194),
      Metric(Duration("timer#max_awt_delay#mean_value"), 70),
      Metric(Duration("timer#max_awt_delay#standard_deviation"), 13),
      Metric(Duration("findUsages#number_of_found_usages#mean_value"), 1384),
      Metric(Duration("findUsages#number_of_found_usages#standard_deviation"), 0),
      Metric(Duration("timer#average_awt_delay#mean_value"), 5),
      Metric(Duration("timer#average_awt_delay#standard_deviation"), 2),
    ))
  }
}