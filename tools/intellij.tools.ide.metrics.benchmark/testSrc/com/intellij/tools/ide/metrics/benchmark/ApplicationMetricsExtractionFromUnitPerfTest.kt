// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.metrics.benchmark

import com.intellij.openapi.application.PathManager
import com.intellij.platform.diagnostic.telemetry.PlatformMetrics
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.tools.ide.metrics.collector.OpenTelemetryJsonMeterCollector
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import io.opentelemetry.api.trace.Span
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

    meter.counterBuilder("custom.long.counter")
      .setDescription("Long counter example")
      .buildWithCallback { it.record(counter.get()) }

    meter.counterBuilder("custom.double.counter")
      .ofDoubles().setDescription("Double counter example")
      .buildWithCallback { it.record(counter.get().toDouble()) }

    meter.gaugeBuilder("custom.long.gauge")
      .ofLongs().setDescription("Long gauge example")
      .buildWithCallback { it.record(counter.get()) }

    meter.gaugeBuilder("custom.double.gauge")
      .setDescription("Double gauge example")
      .buildWithCallback { it.record(234.567) }

    val meterCollector = OpenTelemetryJsonMeterCollector(MetricsSelectionStrategy.SUM) { it.name.contains("custom") }
    val testName = testInfo.testMethod.get().name
    val customSpanName = "custom span"

    val histogram = meter.histogramBuilder("custom.histogram")
      .setDescription("Histogram example")
      .setUnit("someUnit")
      .ofLongs()
      .build()

    Benchmark.newBenchmark(testName) {
      tracer.spanBuilder(customSpanName).use {
        runBlocking { delay(Random.nextInt(50, 100).milliseconds) }
      }

      counter.incrementAndGet()
      (1L..10L).forEach { histogram.record(it) }

      runBlocking { delay(Random.nextInt(50, 100).milliseconds) }
    }
      .attempts(5)
      .withMetricsCollector(meterCollector)
      .start()

    SpanExtractionFromUnitPerfTest.checkMetricsAreFlushedToTelemetryFile(getFullTestName(testInfo, testName), withWarmup = true, customSpanName)
    val meters = meterCollector.collect(PathManager.getLogDir())

    meters.assertMeterIsExported("custom.long.counter", 6)
    meters.assertMeterIsExported("custom.double.counter", 6)

    meters.assertMeterIsExported("custom.long.gauge", 6)
    meters.assertMeterIsExported("custom.double.gauge", 234)

    meters.assertMeterIsExported("custom.histogram.min.someUnit", 1)
    meters.assertMeterIsExported("custom.histogram.max.someUnit", 10)
    meters.assertMeterIsExported("custom.histogram.measurements.count", 60)
    meters.assertMeterIsExported("custom.histogram.median.someUnit", 7)
    meters.assertMeterIsExported("custom.histogram.standard.deviation.someUnit", 2)
    meters.assertMeterIsExported("custom.histogram.95.percentile.someUnit", 10)
    meters.assertMeterIsExported("custom.histogram.99.percentile.someUnit", 10)
    meters.assertMeterIsExported("custom.histogram.mad.someUnit", 2)
    meters.assertMeterIsExported("custom.histogram.range.someUnit", 10000)
  }

  private fun List<PerformanceMetrics.Metric>.assertMeterIsExported(meterName: String, expectedValue: Int) {
    Assertions.assertEquals(this.single { it.id.name == meterName }.value, expectedValue,
                            "$meterName meter should be present in .json meters file")
  }

  @Test
  fun customSpanSubtest(testInfo: TestInfo) {
    val testName = testInfo.testMethod.get().name
    val customSpanName = "custom span"

    val perfTest = Benchmark.newBenchmark(testName) {
      tracer.spanBuilder(customSpanName).use {
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