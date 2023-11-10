// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.metrics.benchmark

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.util.BuildNumber
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestApplicationManager
import com.intellij.tools.ide.metrics.collector.publishing.CIServerBuildInfo
import com.intellij.tools.ide.metrics.collector.publishing.PerformanceMetricsDto
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.nio.file.Files
import java.nio.file.Paths
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.div


/** Class intentionally named *Perf* test.
 * That way it will not be ignored during Aggregator run */
class SpanExtractionFromUnitPerfTest {

  private val openTelemetryReports by lazy {
    Paths.get(this::class.java.classLoader.getResource("opentelemetry")!!.toURI())
  }

  companion object {
    @JvmStatic
    @BeforeAll
    fun initTestApplication() {
      TestApplicationManager.getInstance()
    }
  }

  @Test
  fun unitPerfTestsMetricsExtraction(testInfo: TestInfo) {
    val mainMetricName = "Adding and removing a project library 30 times"

    val extractedMetrics = MetricsExtractor((openTelemetryReports / "open-telemetry-unit-perf-test.json").toFile())
      .waitTillMetricsExported(spanName = mainMetricName)

    Assertions.assertEquals(32, extractedMetrics.single { it.id.name == "attempt.mean.ms" }.value)
    Assertions.assertEquals(29, extractedMetrics.single { it.id.name == "attempt.median.ms" }.value)
    Assertions.assertEquals(29, extractedMetrics.single { it.id.name == "attempt.mode" }.value)
    Assertions.assertEquals(34, extractedMetrics.single { it.id.name == "attempt.range.ms" }.value)
    Assertions.assertEquals(524, extractedMetrics.single { it.id.name == "attempt.sum.ms" }.value)
    Assertions.assertEquals(16, extractedMetrics.single { it.id.name == "attempt.count" }.value)
    Assertions.assertEquals(8, extractedMetrics.single { it.id.name == "attempt.standard.deviation" }.value)
    Assertions.assertEquals(14019, extractedMetrics.single { it.id.name == "total.test.duration.ms" }.value)

    val reportFile = Files.createTempFile("temp", ".json")

    val buildInfo = CIServerBuildInfo(
      "8727723",
      "someBuildType",
      "configurationName",
      "233.5353.98",
      "branch_name",
      String.format("%s/viewLog.html?buildId=%s&buildTypeId=%s", "base_uri", "8727723", "someBuildType"),
      false,
      ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    )

    val metricsDto = PerformanceMetricsDto.create(
      mainMetricName,
      "",
      "",
      testInfo.displayName,
      BuildNumber.fromString("233.SNAPSHOT")!!,
      extractedMetrics,
      buildInfo
    )

    // just invoke serialization to validate that it completes without exceptions
    jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reportFile.toFile(), metricsDto)
  }

  private fun checkMetricsAreFlushedToTelemetryFile(spanName: String) {
    val extractedMetrics = MetricsExtractor().waitTillMetricsExported(spanName = spanName)
    Assertions.assertTrue(extractedMetrics.single { it.id.name == "attempt.mean.ms" }.value != 0L,
                          "Attempt metric should have non 0 value")
    Assertions.assertTrue(extractedMetrics.single { it.id.name == "total.test.duration.ms" }.value != 0L,
                          "Total test duration metric should have non 0 value")
  }

  @Test
  fun flushingTelemetryMetricsShouldNotFailTheTest() {
    val spanName = "simple perf test"
    PlatformTestUtil.startPerformanceTest(spanName, 100) { }.attempts(2).assertTiming()
    checkMetricsAreFlushedToTelemetryFile(spanName)
  }

  @Test
  fun throwingExceptionWillNotAffectMetricsPublishing() {
    val spanName = "perf test throwing exception"
    try {
      PlatformTestUtil.startPerformanceTest(spanName, 100) { throw RuntimeException("Exception text") }.attempts(2).assertTiming()
    }
    catch (t: Throwable) {
      //
    }

    checkMetricsAreFlushedToTelemetryFile(spanName)
  }
}