// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session

import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.advanced.AdvancedSettings
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import com.intellij.terminal.tests.reworked.frontend.session.TerminalRenderingBenchmark.PaintHarness
import com.intellij.terminal.tests.reworked.frontend.session.TerminalRenderingBenchmark.PaintObserver
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Locale
import javax.swing.RepaintManager
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Measures exact log payloads through software paint and complete retained history.
 * Reuses the sessions across workloads. Startup and full text assertions remain outside the measurements.
 */
@TestApplication
internal class TerminalLargeLogPerfTest {
  @Test
  fun `compare exact log workloads`(): Unit = timeoutRunBlocking(15.minutes) {
    val backendNames = selectedNames("ketraterm.benchmark.backends", TerminalEmulatorType.entries.map { it.name })
    val backends = TerminalEmulatorType.entries.filter { it.name in backendNames }
    val defaultWorkloads = listOf("ascii-1mib", "ascii-5mib", "utf8-1mib")
    val workloadNames = selectedNames(
      "ketraterm.benchmark.logWorkloads", defaultWorkloads + "utf8-long-lines-1mib", defaultWorkloads,
    )
    val workloads = workloadNames.map { createTerminalLogWorkload(it) }
    val capacityKey = "new.terminal.output.capacity.kb"
    val linesKey = "terminal.buffer.max.lines.count"
    val previousCapacity = AdvancedSettings.getInt(capacityKey)
    val previousLines = AdvancedSettings.getInt(linesKey)
    val benchmark = TerminalRenderingBenchmark(projectFixture.get())
    val observer = PaintObserver()
    val harnesses = LinkedHashMap<TerminalEmulatorType, PaintHarness>()
    val previousObserver = withContext(Dispatchers.EDT) {
      RepaintManager.currentManager(null).also { RepaintManager.setCurrentManager(observer) }
    }
    var sequence = 30_000
    val failures = ArrayList<String>()
    try {
      AdvancedSettings.setInt(capacityKey, 10_240)
      AdvancedSettings.setInt(linesKey, 180_000)
      for (backend in backends) harnesses[backend] = benchmark.createHarness(this, backend, observer)
      println("LARGE LOG RUNTIME: java=${System.getProperty("java.runtime.version")} vm=${System.getProperty("java.vm.name")} " +
              "maxHeap=${Runtime.getRuntime().maxMemory()}")
      println("LARGE LOG BACKENDS: ${backends.joinToString { it.name }}; sessions=${harnesses.size}; reused across workloads")
      println("LARGE LOG INPUT: loopback characters; UTF-8 encoding costs depend on the adapter; controls add to payload bytes")
      println("LARGE LOG PRE-INPUT IDLE: $benchmarkPreInputIdleMillis ms; outside measurements")
      println("LARGE LOG ENDPOINTS: software-painted marker; both software-painted marker and required complete history")
      println("LARGE LOG VALIDATION: exact retained text after measurements; NFC input; no normalization during comparison")
      println("LARGE LOG EXCLUSIONS: startup, reset, input gap, full text assertions, compositor, and display presentation")
      println("LARGE LOG ALLOCATION: all JVM threads; includes benchmark and platform work; excludes native allocation and retained heap")
      for (workload in workloads) {
        val separateValidation = workload.name == "utf8-long-lines-1mib"
        val input = TerminalRenderingBenchmark.ERASE_MARKER + workload.payload
        val expected = TerminalRenderingBenchmark.textAfterErasingMarker(workload.payload)
        val ghosttyReadinessText = if (separateValidation) expected.replace("\uFE0F", "") else expected
        val samples = backends.associateWith { Samples() }
        val failedBackends = HashSet<TerminalEmulatorType>()
        println("LARGE LOG CONFIG ${workload.name}: payloadBytes=${workload.payloadBytes} utf16Units=${workload.payload.length} " +
                "records=${workload.records} recordBytes=${workload.recordBytes} grid=80x24 warmups=$WARMUP_RUNS " +
                "measurements=$MEASURED_RUNS normalization=NFC historyComparison=exact controls=additional")
        if (separateValidation) println("LARGE LOG MODE ${workload.name}: paint measurements precede separate exact text validation")
        if (separateValidation) {
          println("LARGE LOG READINESS Ghostty: excludes U+FE0F from the text oracle; input and final exact validation preserve it")
        }
        repeat(WARMUP_RUNS + MEASURED_RUNS) { run ->
          for (offset in backends.indices) {
            val backend = backends[(run + offset) % backends.size]
            if (backend in failedBackends) continue
            val harness = harnesses.getValue(backend)
            val painted = try {
              withTimeout(90.seconds) {
                harness.submit(TerminalRenderingBenchmark.CLEAR, sequence++)
                awaitBenchmarkInputGap()
                val readinessText = if (backend == TerminalEmulatorType.Ghostty) ghosttyReadinessText else expected
                harness.submit(
                  input, sequence++, verifyHistory = true, expectedTextBeforeMarker = readinessText, measureAllocations = true,
                )
              }
            }
            catch (error: TimeoutCancellationException) {
              currentCoroutineContext().ensureActive()
              recordFailure(failures, backend, workload.name, run, error)
              failedBackends.add(backend)
              continue
            }
            catch (error: AssertionError) {
              currentCoroutineContext().ensureActive()
              recordFailure(failures, backend, workload.name, run, error)
              failedBackends.add(backend)
              continue
            }
            if (run >= WARMUP_RUNS) {
              val sample = run - WARMUP_RUNS
              val result = samples.getValue(backend)
              result.paintedNanos[sample] = painted
              result.completeNanos[sample] = maxOf(painted, harness.documentHistoryNanos, harness.historyNanos)
              result.markerBytes[sample] = harness.markerAllocatedBytes
              result.completeBytes[sample] = maxOf(harness.markerAllocatedBytes, harness.historyAllocatedBytes)
            }
          }
        }
        for ((backend, result) in samples) {
          if (backend in failedBackends) continue
          assertThat(result.paintedNanos.all { it > 0 }).isTrue()
          assertThat(result.completeNanos.indices.all { result.completeNanos[it] >= result.paintedNanos[it] }).isTrue()
          assertThat(result.markerBytes.all { it >= 0 }).isTrue()
          assertThat(result.completeBytes.indices.all { result.completeBytes[it] >= result.markerBytes[it] }).isTrue()
          printSamples(backend, workload.name, "input-to-paint-ms", result.paintedNanos, milliseconds = true)
          printSamples(backend, workload.name, "painted-marker-bytes", result.markerBytes)
          printSamples(backend, workload.name, "input-to-paint-and-history-ms", result.completeNanos, milliseconds = true)
          printSamples(backend, workload.name, "painted-marker-and-history-bytes", result.completeBytes)
        }
        if (separateValidation) {
          for (backend in backends) {
            if (backend in failedBackends) continue
            val harness = harnesses.getValue(backend)
            try {
              withTimeout(90.seconds) {
                harness.submit(TerminalRenderingBenchmark.CLEAR, sequence++)
                awaitBenchmarkInputGap()
                harness.submit(input, sequence++, verifyHistory = true, expectedTextBeforeMarker = expected)
              }
              println("LARGE LOG VALIDATION PASS ${backend.name} ${workload.name}")
            }
            catch (error: TimeoutCancellationException) {
              currentCoroutineContext().ensureActive()
              recordFailure(failures, backend, workload.name, WARMUP_RUNS + MEASURED_RUNS, error)
            }
            catch (error: AssertionError) {
              currentCoroutineContext().ensureActive()
              recordFailure(failures, backend, workload.name, WARMUP_RUNS + MEASURED_RUNS, error)
            }
          }
        }
      }
      assertThat(failures).describedAs("Some log workloads failed").isEmpty()
    }
    finally {
      withContext(NonCancellable) {
        try {
          for (harness in harnesses.values) harness.close()
        }
        finally {
          withContext(Dispatchers.EDT) { RepaintManager.setCurrentManager(previousObserver) }
          AdvancedSettings.setInt(capacityKey, previousCapacity)
          AdvancedSettings.setInt(linesKey, previousLines)
        }
      }
    }
  }

  private fun recordFailure(failures: MutableList<String>, backend: TerminalEmulatorType, workload: String, run: Int, error: Throwable) {
    val stage = when {
      run < WARMUP_RUNS -> "warmup"
      run < WARMUP_RUNS + MEASURED_RUNS -> "measurement"
      else -> "validation"
    }
    val message = error.message.orEmpty().take(240).replace('\r', ' ').replace('\n', ' ')
    val failure = "LARGE LOG FAILURE ${backend.name} $workload: run=$run stage=$stage " +
                  "cause=${error.javaClass.simpleName} message=$message"
    failures.add(failure)
    println(failure)
  }

  private fun selectedNames(property: String, available: List<String>, defaults: List<String> = available): List<String> {
    val requested = System.getProperty(property)?.split(',')?.map { it.trim() } ?: return defaults
    require(requested.all { it in available }) { "Unknown choices for $property: $requested" }
    val selected = available.filter { it in requested }
    require(selected.isNotEmpty()) { "No choices for $property" }
    return selected
  }

  private fun printSamples(
    backend: TerminalEmulatorType,
    workload: String,
    metric: String,
    values: LongArray,
    milliseconds: Boolean = false,
  ) {
    val formatted = if (milliseconds) values.joinToString { "%.3f".format(Locale.ROOT, it / 1_000_000.0) }
    else values.joinToString()
    println("LARGE LOG SAMPLES ${backend.name} $workload $metric: $formatted")
  }

  private class Samples {
    val paintedNanos = LongArray(MEASURED_RUNS)
    val completeNanos = LongArray(MEASURED_RUNS)
    val markerBytes = LongArray(MEASURED_RUNS)
    val completeBytes = LongArray(MEASURED_RUNS)
  }

  companion object {
    private val projectFixture = projectFixture()
    private const val WARMUP_RUNS = 5
    private const val MEASURED_RUNS = 9
  }
}
