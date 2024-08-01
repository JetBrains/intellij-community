// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.metrics.benchmark

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.BuildNumber
import com.intellij.testFramework.BenchmarkTestInfo
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

/** Class intentionally named *Perf* (and not a *Performance*) test.
 * That way it will not be ignored during Aggregator run */
class SpanExtractionFromUnitPerfTest {
  companion object {
    fun checkMetricsAreFlushedToTelemetryFile(spanName: String, withWarmup: Boolean = true, vararg customSpanNames: String) {
      val extractedMetrics = runBlocking {
        BenchmarksSpanMetricsCollector(spanName).collect(PathManager.getLogDir())
      }

      if (withWarmup) {
        // warmup metrics
        Assertions.assertTrue(extractedMetrics.single { it.id.name == "warmup.attempt.mean.ms" }.value != 0,
                              "Attempt metric should have non 0 value")
        Assertions.assertTrue(extractedMetrics.single { it.id.name == "warmup.total.test.duration.ms" }.value != 0,
                              "Total test duration metric should have non 0 value")
      }

      // measured metrics
      Assertions.assertTrue(extractedMetrics.single { it.id.name == "attempt.mean.ms" }.value != 0,
                            "Attempt metric should have non 0 value")
      Assertions.assertTrue(extractedMetrics.single { it.id.name == "total.test.duration.ms" }.value != 0,
                            "Total test duration metric should have non 0 value")

      customSpanNames.forEach { customName ->
        Assertions.assertTrue(extractedMetrics.single { it.id.name == customName }.value != 0,
                              "$customName metric should have non 0 value")
      }
    }
  }

  private val openTelemetryReports by lazy {
    Paths.get(this::class.java.classLoader.getResource("telemetry")!!.toURI())
  }

  @Test
  fun `unit perf test metrics extraction - microsecond precision`(testInfo: TestInfo) = runBlocking {
    val mainMetricName = "simple perf test"

    val extractedMetrics = BenchmarksSpanMetricsCollector(spanName = mainMetricName,
                                                          telemetryJsonFile = (openTelemetryReports / "open-telemetry-microseconds-unit-perf-test.json"))
      .collect(PathManager.getLogDir())

    // warmup metrics
    Assertions.assertEquals(243, extractedMetrics.single { it.id.name == "warmup.attempt.mean.ms" }.value)
    Assertions.assertEquals(243, extractedMetrics.single { it.id.name == "warmup.attempt.median.ms" }.value)
    Assertions.assertEquals(0, extractedMetrics.single { it.id.name == "warmup.attempt.range.ms" }.value)
    Assertions.assertEquals(243, extractedMetrics.single { it.id.name == "warmup.attempt.sum.ms" }.value)
    Assertions.assertEquals(1, extractedMetrics.single { it.id.name == "warmup.attempt.count" }.value)
    Assertions.assertEquals(0, extractedMetrics.single { it.id.name == "warmup.attempt.standard.deviation" }.value)
    Assertions.assertEquals(379, extractedMetrics.single { it.id.name == "warmup.total.test.duration.ms" }.value)

    // measured metrics
    Assertions.assertEquals(306, extractedMetrics.single { it.id.name == "attempt.mean.ms" }.value)
    Assertions.assertEquals(324, extractedMetrics.single { it.id.name == "attempt.median.ms" }.value)
    Assertions.assertEquals(376, extractedMetrics.single { it.id.name == "attempt.range.ms" }.value)
    Assertions.assertEquals(920, extractedMetrics.single { it.id.name == "attempt.sum.ms" }.value)
    Assertions.assertEquals(3, extractedMetrics.single { it.id.name == "attempt.count" }.value)
    Assertions.assertEquals(153, extractedMetrics.single { it.id.name == "attempt.standard.deviation" }.value)
    Assertions.assertEquals(2004, extractedMetrics.single { it.id.name == "total.test.duration.ms" }.value)

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

  @Test
  fun `unit perf test metrics extraction - nanosecond precision`(testInfo: TestInfo) = runBlocking {
    val mainMetricName = "simple perf test"

    val extractedMetrics = BenchmarksSpanMetricsCollector(spanName = mainMetricName,
                                                          telemetryJsonFile = (openTelemetryReports / "open-telemetry-unit-perf-test.json"))
      .collect(PathManager.getLogDir())

    // warmup metrics
    Assertions.assertEquals(243, extractedMetrics.single { it.id.name == "warmup.attempt.mean.ms" }.value)
    Assertions.assertEquals(243, extractedMetrics.single { it.id.name == "warmup.attempt.median.ms" }.value)
    Assertions.assertEquals(0, extractedMetrics.single { it.id.name == "warmup.attempt.range.ms" }.value)
    Assertions.assertEquals(243, extractedMetrics.single { it.id.name == "warmup.attempt.sum.ms" }.value)
    Assertions.assertEquals(1, extractedMetrics.single { it.id.name == "warmup.attempt.count" }.value)
    Assertions.assertEquals(0, extractedMetrics.single { it.id.name == "warmup.attempt.standard.deviation" }.value)
    Assertions.assertEquals(379, extractedMetrics.single { it.id.name == "warmup.total.test.duration.ms" }.value)

    // measured metrics
    Assertions.assertEquals(306, extractedMetrics.single { it.id.name == "attempt.mean.ms" }.value)
    Assertions.assertEquals(324, extractedMetrics.single { it.id.name == "attempt.median.ms" }.value)
    Assertions.assertEquals(376, extractedMetrics.single { it.id.name == "attempt.range.ms" }.value)
    Assertions.assertEquals(920, extractedMetrics.single { it.id.name == "attempt.sum.ms" }.value)
    Assertions.assertEquals(3, extractedMetrics.single { it.id.name == "attempt.count" }.value)
    Assertions.assertEquals(153, extractedMetrics.single { it.id.name == "attempt.standard.deviation" }.value)
    Assertions.assertEquals(2004, extractedMetrics.single { it.id.name == "total.test.duration.ms" }.value)

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

  @Test
  fun perfTestWithSubtests(testInfo: TestInfo) {
    fun runSubTest(subtestName: String) {
      Benchmark.newBenchmark(testInfo.testMethod.get().name) {
        runBlocking { delay(Random.nextInt(100, 500).milliseconds) }
      }.startAsSubtest(subtestName)
    }

    runSubTest("subtest1")
    runSubTest("subtest2")
  }

  @Test
  fun flushingTelemetryMetricsShouldNotFailTheTest() {
    val spanName = "simple perf test"
    val uniqueTestName = Benchmark.newBenchmark(spanName) {
      runBlocking { delay(Random.nextInt(100, 500).milliseconds) }
    }.run {
      start()
      uniqueTestName
    }
    checkMetricsAreFlushedToTelemetryFile(uniqueTestName)
  }

  @Test
  fun throwingExceptionWillNotAffectMetricsPublishing() {
    val spanName = "perf test throwing exception"
    var perfTest: BenchmarkTestInfo? = null

    try {
      perfTest = Benchmark.newBenchmark(spanName) {
        runBlocking { delay(Random.nextInt(100, 500).milliseconds) }
        throw RuntimeException("Exception text")
      }
      perfTest.apply {
        warmupIterations(0)
        start()
      }
    }
    catch (t: Throwable) {
      //
    }

    checkMetricsAreFlushedToTelemetryFile(perfTest!!.uniqueTestName, withWarmup = false)
  }
}