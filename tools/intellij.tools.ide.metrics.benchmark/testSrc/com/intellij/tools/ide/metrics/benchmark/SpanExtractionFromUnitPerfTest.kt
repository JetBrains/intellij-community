// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.metrics.benchmark

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.util.BuildNumber
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.tools.ide.metrics.collector.publishing.CIServerBuildInfo
import com.intellij.tools.ide.metrics.collector.publishing.PerformanceMetricsDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.nio.file.Files
import java.nio.file.Paths
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.div
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds


/** Class intentionally named *Perf* test.
 * That way it will not be ignored during Aggregator run */
class SpanExtractionFromUnitPerfTest {

  private val openTelemetryReports by lazy {
    Paths.get(this::class.java.classLoader.getResource("opentelemetry")!!.toURI())
  }

  @Test
  fun unitPerfTestsMetricsExtraction(testInfo: TestInfo) {
    val mainMetricName = "simple perf test"

    val extractedMetrics = MetricsExtractor((openTelemetryReports / "open-telemetry-unit-perf-test.json").toFile())
      .waitTillMetricsExported(spanName = mainMetricName)

    // warmup metrics
    Assertions.assertEquals(243, extractedMetrics.single { it.id.name == "warmup.attempt.mean.ms" }.value)
    Assertions.assertEquals(243, extractedMetrics.single { it.id.name == "warmup.attempt.median.ms" }.value)
    Assertions.assertEquals(0, extractedMetrics.single { it.id.name == "warmup.attempt.range.ms" }.value)
    Assertions.assertEquals(243, extractedMetrics.single { it.id.name == "warmup.attempt.sum.ms" }.value)
    Assertions.assertEquals(1, extractedMetrics.single { it.id.name == "warmup.attempt.count" }.value)
    Assertions.assertEquals(0, extractedMetrics.single { it.id.name == "warmup.attempt.standard.deviation" }.value)
    Assertions.assertEquals(379, extractedMetrics.single { it.id.name == "warmup.total.test.duration.ms" }.value)

    // measured metrics
    Assertions.assertEquals(307, extractedMetrics.single { it.id.name == "attempt.mean.ms" }.value)
    Assertions.assertEquals(324, extractedMetrics.single { it.id.name == "attempt.median.ms" }.value)
    Assertions.assertEquals(375, extractedMetrics.single { it.id.name == "attempt.range.ms" }.value)
    Assertions.assertEquals(921, extractedMetrics.single { it.id.name == "attempt.sum.ms" }.value)
    Assertions.assertEquals(3, extractedMetrics.single { it.id.name == "attempt.count" }.value)
    Assertions.assertEquals(153, extractedMetrics.single { it.id.name == "attempt.standard.deviation" }.value)
    Assertions.assertEquals(2005, extractedMetrics.single { it.id.name == "total.test.duration.ms" }.value)

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

  private fun checkMetricsAreFlushedToTelemetryFile(spanName: String, withWarmup: Boolean = true) {
    val extractedMetrics = MetricsExtractor().waitTillMetricsExported(spanName = spanName)

    if (withWarmup) {
      // warmup metrics
      Assertions.assertTrue(extractedMetrics.single { it.id.name == "warmup.attempt.mean.ms" }.value != 0L,
                            "Attempt metric should have non 0 value")
      Assertions.assertTrue(extractedMetrics.single { it.id.name == "warmup.total.test.duration.ms" }.value != 0L,
                            "Total test duration metric should have non 0 value")
    }

    // measured metrics
    Assertions.assertTrue(extractedMetrics.single { it.id.name == "attempt.mean.ms" }.value != 0L,
                          "Attempt metric should have non 0 value")
    Assertions.assertTrue(extractedMetrics.single { it.id.name == "total.test.duration.ms" }.value != 0L,
                          "Total test duration metric should have non 0 value")
  }

  @Test
  fun flushingTelemetryMetricsShouldNotFailTheTest() {
    val spanName = "simple perf test"
    PlatformTestUtil.startPerformanceTest(spanName, 100) {
      runBlocking { delay(Random.nextInt(100, 500).milliseconds) }
    }.assertTiming()
    checkMetricsAreFlushedToTelemetryFile(spanName)
  }

  @Test
  fun throwingExceptionWillNotAffectMetricsPublishing() {
    val spanName = "perf test throwing exception"
    try {
      PlatformTestUtil.startPerformanceTest(spanName, 100) {
        runBlocking { delay(Random.nextInt(100, 500).milliseconds) }
        throw RuntimeException("Exception text")
      }.warmupIterations(0).assertTiming()
    }
    catch (t: Throwable) {
      //
    }

    checkMetricsAreFlushedToTelemetryFile(spanName, withWarmup = false)
  }
}