// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.metrics.benchmark

import com.intellij.openapi.application.PathManager
import com.intellij.platform.diagnostic.telemetry.PlatformMetrics
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.runWithSpan
import com.intellij.platform.testFramework.diagnostic.MetricsAggregation
import com.intellij.platform.testFramework.diagnostic.TelemetryMeterCollector
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

@JvmField
internal val ExtractionMetricsScope: Scope = Scope("ExtractionMetricsScope", PlatformMetrics)

/** Class intentionally named *Perf* (and not a *Performance*) test.
 * That way it will not be ignored during Aggregator run */
@TestApplication
class ApplicationMetricsExtractionFromUnitPerfTest {
  private val tracer = TelemetryManager.getTracer(ExtractionMetricsScope)

  private fun getFullTestName(testInfo: TestInfo, launchName: String) =
    "${testInfo.testClass.get().name}.${testInfo.testMethod.get().name} - $launchName"

  @Test
  fun reportingAnyCustomMetricsFromPerfTest(testInfo: TestInfo) {
    val counter: AtomicLong = AtomicLong()
    val meter = TelemetryManager.getMeter(ExtractionMetricsScope)

    meter.counterBuilder("custom.counter")
      .setUnit("ms").setDescription("Counter example")
      .buildWithCallback { it.record(counter.get()) }

    val meterCollector = TelemetryMeterCollector(MetricsAggregation.SUM) { it.contains("custom") }
    val testName = testInfo.testMethod.get().name
    val customSpanName = "custom span"

    val histogram = meter.histogramBuilder("custom.histogram")
      .setDescription("Histogram example")
      .setUnit("ns")
      .ofLongs()
      .build()

    PlatformTestUtil.newPerformanceTest(testName) {
      runWithSpan(tracer, customSpanName) {
        runBlocking { delay(Random.nextInt(50, 100).milliseconds) }
      }

      counter.incrementAndGet()
      (1L..10L).forEach { histogram.record(it) }

      runBlocking { delay(Random.nextInt(50, 100).milliseconds) }
    }
      .attempts(5)
      .withTelemetryMeters(meterCollector)
      .start()

    SpanExtractionFromUnitPerfTest.checkMetricsAreFlushedToTelemetryFile(getFullTestName(testInfo, testName), withWarmup = true, customSpanName)
    val meters = meterCollector.convertToCompleteMetricsCollector().collect(PathManager.getLogDir())

    Assertions.assertTrue(meters.count { it.id.name == "custom.counter" } == 1, "Counter meter should be present in .json meters file")
  }

  @Test
  fun customSpanSubtest(testInfo: TestInfo) {
    val testName = testInfo.testMethod.get().name
    val customSpanName = "custom span"

    val perfTest = PlatformTestUtil.newPerformanceTest(testName) {
      runWithSpan(tracer, customSpanName) {
        runBlocking { delay(Random.nextInt(50, 100).milliseconds) }
      }

      runBlocking { delay(Random.nextInt(50, 100).milliseconds) }
    }

    perfTest.startAsSubtest("launch1")
    SpanExtractionFromUnitPerfTest.checkMetricsAreFlushedToTelemetryFile(getFullTestName(testInfo, "launch1"), withWarmup = true, customSpanName)

    perfTest.startAsSubtest("launch2")
    SpanExtractionFromUnitPerfTest.checkMetricsAreFlushedToTelemetryFile(getFullTestName(testInfo, "launch2"), withWarmup = true, customSpanName)
  }
}