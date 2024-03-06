package com.intellij.tools.ide.metrics.collector

import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import kotlin.io.path.div

class MetersJsonCollectionTest {

  @Test
  fun testContainsInFilter() {
    val metrics = OpenTelemetryJsonMeterCollector(metricsSelectionStrategy = MetricsSelectionStrategy.LATEST,
                                                  meterFilter = { true }
    ).collect(openTelemetryReports / "meters")

    metrics.shouldNotBeEmpty()

    // TODO: add validation for each metric type

    //val metric = metrics.first()
    //metric.id.name shouldBe "testMetric"
    //metric.value shouldBe 100
    //metric.compareSetting shouldBe CompareSetting.notComparing
  }
}