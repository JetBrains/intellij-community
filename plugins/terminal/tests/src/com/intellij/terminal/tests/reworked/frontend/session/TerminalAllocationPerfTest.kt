// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session

import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.advanced.AdvancedSettings
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import com.intellij.terminal.tests.reworked.frontend.session.TerminalRenderingBenchmark.PaintHarness
import com.intellij.terminal.tests.reworked.frontend.session.TerminalRenderingBenchmark.PaintObserver
import com.intellij.terminal.tests.reworked.frontend.session.TerminalRenderingBenchmark.Workload
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.sun.management.ThreadMXBean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.lang.management.ManagementFactory
import javax.swing.RepaintManager
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Separates session startup, warmup, repeated workload cycles, and disposal.
 * JVM allocation counters include platform and benchmark work on all threads.
 * Allocation does not establish object lifetime or retained heap size.
 */
@TestApplication
internal class TerminalAllocationPerfTest {
  @Test
  fun `compare one MiB allocation through marker and complete history`(): Unit = timeoutRunBlocking(5.minutes) {
    println("ALLOCATION ENDPOINT CONFIG: 6000 lines; 1024890 bytes; 80x24; 5 warmups; 9 measurements")
    measureEndpoints(TerminalRenderingBenchmark(projectFixture.get()), Workload("one-mib-log", oneMiBLogPayload()), "ALLOCATION ENDPOINT SAMPLES")
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("endpointCases")
  fun `compare recurring output allocation through paint and complete history`(backend: TerminalEmulatorType, workload: Workload): Unit = timeoutRunBlocking(3.minutes) {
    measureEndpoints(TerminalRenderingBenchmark(projectFixture.get()), workload, backends = listOf(backend))
  }

  private suspend fun CoroutineScope.measureEndpoints(
    benchmark: TerminalRenderingBenchmark,
    workload: Workload,
    samplePrefix: String = "ALLOCATION WORKLOAD SAMPLES ${workload.name}",
    backends: List<TerminalEmulatorType> = selectedBackends(TerminalEmulatorType.entries),
  ) {
    val capacityKey = "new.terminal.output.capacity.kb"
    val linesKey = "terminal.buffer.max.lines.count"
    val oldCapacity = AdvancedSettings.getInt(capacityKey)
    val oldLines = AdvancedSettings.getInt(linesKey)
    val observer = PaintObserver()
    val harnesses = LinkedHashMap<TerminalEmulatorType, PaintHarness>()
    val marker = backends.associateWith { EndpointSamples() }
    val complete = backends.associateWith { EndpointSamples() }
    val payload = TerminalRenderingBenchmark.ERASE_MARKER + workload.payload
    val prelude = TerminalRenderingBenchmark.ERASE_MARKER + workload.prelude
    val expected = workload.expectedTextBeforeMarker()
    var sequence = 20_000
    val previous = withContext(Dispatchers.EDT) {
      RepaintManager.currentManager(null).also { RepaintManager.setCurrentManager(observer) }
    }
    try {
      AdvancedSettings.setInt(capacityKey, 4096)
      AdvancedSettings.setInt(linesKey, 60_000)
      for (backend in backends) harnesses[backend] = benchmark.createHarness(this, backend, observer)
      println("ALLOCATION ENDPOINT WORKLOAD: ${workload.name}; $WARMUP_RUNS warmups; $MEASURED_RUNS measurements")
      println("ALLOCATION PRE-INPUT IDLE: $benchmarkPreInputIdleMillis ms; outside counters")
      println("ALLOCATION ENDPOINTS: input to painted marker; input through both painted marker and required complete history")
      println("ALLOCATION SCOPE: all JVM threads; includes benchmark/platform activity; excludes native allocation and retained heap")
      println("ALLOCATION EXCLUSIONS: setup, clear handshake, input gap, whole-text verification; no forced GC")
      println("ALLOCATION HISTORY: editor document for JediTerm and Ghostty, native rows for KetraTerm")
      repeat(WARMUP_RUNS + MEASURED_RUNS) { run ->
        for (index in backends.indices) {
          val backend = backends[(run + index) % backends.size]
          val harness = harnesses.getValue(backend)
          val painted = withTimeout(30.seconds) {
            harness.submit(TerminalRenderingBenchmark.CLEAR, sequence++)
            if (workload.prelude.isNotEmpty()) harness.submit(prelude, sequence++, verifyHistory = true)
            awaitBenchmarkInputGap()
            harness.submit(payload, sequence++, verifyHistory = true, measureAllocations = true, expectedTextBeforeMarker = expected)
          }
          if (run >= WARMUP_RUNS) {
            val sample = run - WARMUP_RUNS
            marker.getValue(backend).record(sample, harness.markerAllocatedBytes, painted)
            complete.getValue(backend).record(sample, maxOf(harness.markerAllocatedBytes, harness.historyAllocatedBytes),
                                              maxOf(painted, harness.documentHistoryNanos, harness.historyNanos))
          }
        }
      }
      for (backend in backends) {
        marker.getValue(backend).report(samplePrefix, backend, workload.name, "painted-marker")
        complete.getValue(backend).report(samplePrefix, backend, workload.name, "painted-marker-and-history")
      }
    }
    finally {
      withContext(NonCancellable) {
        try { for (harness in harnesses.values) harness.close() }
        finally {
          withContext(Dispatchers.EDT) { RepaintManager.setCurrentManager(previous) }
          AdvancedSettings.setInt(capacityKey, oldCapacity)
          AdvancedSettings.setInt(linesKey, oldLines)
        }
      }
    }
  }

  private class EndpointSamples {
    private val bytes = LongArray(MEASURED_RUNS)
    private var elapsedNanos = 0L

    fun record(sample: Int, allocatedBytes: Long, nanos: Long) {
      check(nanos > 0 && allocatedBytes >= 0)
      bytes[sample] = allocatedBytes
      elapsedNanos += nanos
    }

    fun report(prefix: String, backend: TerminalEmulatorType, workload: String, endpoint: String) {
      val totalBytes = bytes.sum()
      val mib = totalBytes / 1048576.0
      println("$prefix ${backend.name} $endpoint-bytes: " + bytes.joinToString())
      println("ALLOCATION ENDPOINT TOTAL backend=${backend.name} workload=$workload endpoint=$endpoint operations=$MEASURED_RUNS " +
              "totalBytes=$totalBytes elapsedNanos=$elapsedNanos MiB/s=${mib * 1e9 / elapsedNanos} MiB/op=${mib / MEASURED_RUNS}")
    }
  }

  @Test
  fun `measure startup and recurring allocation pressure`(): Unit = timeoutRunBlocking(10.minutes) {
    val benchmark = TerminalRenderingBenchmark(projectFixture.get())
    val workloads = TerminalRenderingBenchmark.workloads()
    val selected = selectedBackends(TerminalEmulatorType.entries)
    val meter = JvmAllocationMeter()
    val observer = PaintObserver()
    val previous = withContext(Dispatchers.EDT) {
      RepaintManager.currentManager(null).also { RepaintManager.setCurrentManager(observer) }
    }
    println("ALLOCATION CONFIG: $WARMUP_RUNS warmup runs; $MEASURED_RUNS samples; $CYCLES_PER_SAMPLE cycles per steady sample")
    println("ALLOCATION SCOPE: all JVM threads; includes platform and benchmark work; excludes native allocation and retained heap")
    println("ALLOCATION STATUS: exploratory JVM activity windows; not isolated per-operation or total backend costs")
    println("ALLOCATION OVERLAP: deferred history may cross reset/output boundaries and outlive the final marker")
    println("ALLOCATION STARTUP: first marker on a new session; no reset command")
    println("ALLOCATION CYCLE: reset, history prelude, workload, and verified software paints; no forced GC")
    println("ALLOCATION JVM: ${System.getProperty("java.runtime.version")}; maxHeap=${Runtime.getRuntime().maxMemory()}")
    println("ALLOCATION GC: ${ManagementFactory.getGarbageCollectorMXBeans().joinToString { it.name }}")
    var sequence = 1
    var live: PaintHarness? = null
    try {
      for (backend in selected) {
        suspend fun CoroutineScope.start(): PaintHarness {
          val harness = benchmark.createHarness(this, backend, observer)
          live = harness
          withTimeout(30.seconds) { harness.submit("", sequence++) }
          return harness
        }

        suspend fun dispose(harness: PaintHarness) {
          harness.close()
          live = null
        }

        val first = meter.measure(backend, "first-session", "none", 0, 1) { start() }
        meter.measure(backend, "first-disposal", "none", 0, 1) { dispose(first) }
        meter.measure(backend, "startup-warmup", "none", 0, WARMUP_RUNS) {
          repeat(WARMUP_RUNS) { dispose(start()) }
        }
        repeat(MEASURED_RUNS) { sample ->
          val harness = meter.measure(backend, "session-startup", "none", sample, 1) { start() }
          if (sample < MEASURED_RUNS - 1) {
            meter.measure(backend, "session-disposal", "none", sample, 1) { dispose(harness) }
          }
        }
        val harness = checkNotNull(live)
        for (workload in workloads) {
          val reset = TerminalRenderingBenchmark.CLEAR + workload.prelude
          val payloadBytes = workload.payload.toByteArray(Charsets.UTF_8).size.toLong()
          val controlBytes = reset.toByteArray(Charsets.UTF_8).size.toLong()
          val windows = meter.cycleWindows()
          suspend fun cycle() {
            withTimeout(30.seconds) {
              windows.measure(reset = true) { harness.submit(reset, sequence++) }
              windows.measure(reset = false) { harness.submit(workload.payload, sequence++) }
            }
          }
          meter.measure(backend, "workload-warmup", workload.name, 0, WARMUP_RUNS, payloadBytes, controlBytes) {
            repeat(WARMUP_RUNS) { cycle() }
          }
          repeat(MEASURED_RUNS) { sample ->
            windows.clear()
            meter.measure(backend, "steady-cycle", workload.name, sample, CYCLES_PER_SAMPLE, payloadBytes, controlBytes) {
              repeat(CYCLES_PER_SAMPLE) { cycle() }
            }
            windows.report(backend, workload.name, sample, CYCLES_PER_SAMPLE)
          }
        }
        meter.measure(backend, "session-disposal", "none", MEASURED_RUNS - 1, 1) { dispose(harness) }
      }
    }
    finally {
      withContext(NonCancellable) {
        try { live?.close() }
        finally { withContext(Dispatchers.EDT) { RepaintManager.setCurrentManager(previous) } }
      }
    }
  }

  private class JvmAllocationMeter {
    private val bean = (ManagementFactory.getThreadMXBean() as ThreadMXBean).also {
      check(it.isThreadAllocatedMemorySupported) { "The JVM does not support allocation counters" }
      it.isThreadAllocatedMemoryEnabled = true
      check(it.totalThreadAllocatedBytes >= 0) { "The JVM does not expose a total allocation counter" }
    }
    private val collectors = ManagementFactory.getGarbageCollectorMXBeans()
    private val counts = LongArray(collectors.size)
    private val times = LongArray(collectors.size)

    fun cycleWindows(): CycleAllocationWindows = CycleAllocationWindows(bean)

    suspend fun <T> measure(
      backend: TerminalEmulatorType,
      phase: String,
      workload: String,
      sample: Int,
      operations: Int,
      payloadBytes: Long = 0,
      controlBytes: Long = 0,
      action: suspend () -> T,
    ): T {
      for (index in collectors.indices) {
        counts[index] = collectors[index].collectionCount
        times[index] = collectors[index].collectionTime
        check(counts[index] >= 0 && times[index] >= 0) { "The JVM does not expose GC counters" }
      }
      val before = bean.totalThreadAllocatedBytes
      val start = System.nanoTime()
      val result = action()
      val nanos = System.nanoTime() - start
      val allocated = bean.totalThreadAllocatedBytes - before
      var gcCount = 0L
      var gcMillis = 0L
      for (index in collectors.indices) {
        gcCount += collectors[index].collectionCount - counts[index]
        gcMillis += collectors[index].collectionTime - times[index]
      }
      check(allocated >= 0 && nanos > 0 && gcCount >= 0 && gcMillis >= 0) { "The JVM counters moved backwards" }
      println("ALLOCATION SAMPLE backend=${backend.name} phase=$phase workload=$workload sample=$sample " +
              "operations=$operations bytes=$allocated nanos=$nanos gcCount=$gcCount gcMillis=$gcMillis " +
              "payloadBytes=${payloadBytes * operations} controlBytes=${controlBytes * operations}")
      return result
    }
  }

  private class CycleAllocationWindows(private val bean: ThreadMXBean) {
    private var resetBytes = 0L
    private var resetNanos = 0L
    private var outputBytes = 0L
    private var outputNanos = 0L

    fun clear() {
      resetBytes = 0
      resetNanos = 0
      outputBytes = 0
      outputNanos = 0
    }

    suspend fun measure(reset: Boolean, action: suspend () -> Unit) {
      val before = bean.totalThreadAllocatedBytes
      val start = System.nanoTime()
      action()
      val nanos = System.nanoTime() - start
      val bytes = bean.totalThreadAllocatedBytes - before
      check(bytes >= 0 && nanos > 0) { "The JVM allocation counter moved backwards" }
      if (reset) {
        resetBytes += bytes
        resetNanos += nanos
      }
      else {
        outputBytes += bytes
        outputNanos += nanos
      }
    }

    fun report(backend: TerminalEmulatorType, workload: String, sample: Int, operations: Int) {
      println("ALLOCATION WINDOW backend=${backend.name} phase=reset workload=$workload sample=$sample " +
              "operations=$operations bytes=$resetBytes nanos=$resetNanos")
      println("ALLOCATION WINDOW backend=${backend.name} phase=output workload=$workload sample=$sample " +
              "operations=$operations bytes=$outputBytes nanos=$outputNanos")
    }
  }

  companion object {
    @JvmStatic
    private fun endpointCases(): List<Arguments> {
      val backends = selectedBackends(TerminalEmulatorType.entries)
      return TerminalRenderingBenchmark.workloads().flatMap { workload -> backends.map { Arguments.of(it, workload) } }
    }

    private fun selectedBackends(defaults: List<TerminalEmulatorType>): List<TerminalEmulatorType> =
      System.getenv("TERMINAL_ALLOCATION_BACKEND")?.let { listOf(TerminalEmulatorType.valueOf(it)) } ?: defaults

    private val projectFixture = projectFixture()
    private const val WARMUP_RUNS = 5
    private const val MEASURED_RUNS = 9
    private const val CYCLES_PER_SAMPLE = 8
  }
}
