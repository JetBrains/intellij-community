// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.tests.reworked.frontend.session.ketraterm.retainedText
import com.intellij.terminal.frontend.session.ketraterm.KetraTermTerminalSession
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.impl.TerminalViewImpl
import com.intellij.terminal.frontend.view.ketraterm.KetraTermTerminalView
import com.intellij.terminal.tests.reworked.util.LoopbackTtyConnector
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.asDisposable
import jdk.jfr.Configuration
import jdk.jfr.Recording
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Reuses one session per backend across all workloads at each endpoint.
 * Each workload uses five warmup runs on the same session before nine measured runs.
 * Screen and history clearing finish before each timed input. Painting is excluded.
 */
@TestApplication
internal class ReusedTerminalSessionPerfTest {
  @Test
  fun `compare reused session content event latency`(): Unit = timeoutRunBlocking(5.minutes) {
    compare(Endpoint.CONTENT)
  }

  @Test
  fun `compare reused session frontend application latency`(): Unit = timeoutRunBlocking(5.minutes) {
    compare(Endpoint.FRONTEND)
  }

  @Test
  fun `compare reused session complete history application latency`(): Unit = timeoutRunBlocking(5.minutes) {
    val capacityKey = "new.terminal.output.capacity.kb"
    val linesKey = "terminal.buffer.max.lines.count"
    val previousCapacity = AdvancedSettings.getInt(capacityKey)
    val previousLines = AdvancedSettings.getInt(linesKey)
    try {
      AdvancedSettings.setInt(capacityKey, 4096)
      AdvancedSettings.setInt(linesKey, 60_000)
      println("Complete-history limits: 4096 KiB output capacity; 60000 JediTerm history rows.")
      compare(Endpoint.COMPLETE_HISTORY)
    }
    finally {
      AdvancedSettings.setInt(capacityKey, previousCapacity)
      AdvancedSettings.setInt(linesKey, previousLines)
    }
  }

  @Test
  fun `compare reused session complete history`(): Unit = timeoutRunBlocking(5.minutes) {
    compareHistory(lines = 6_000, capacityKiB = 4096, historyRows = 60_000)
  }

  @Test
  fun `compare five MiB complete history`(): Unit = timeoutRunBlocking(5.minutes) {
    compareHistory(lines = 30_685, capacityKiB = 10_240, historyRows = 160_000)
  }

  private suspend fun CoroutineScope.compareHistory(lines: Int, capacityKiB: Int, historyRows: Int) {
    val capacityKey = "new.terminal.output.capacity.kb"
    val linesKey = "terminal.buffer.max.lines.count"
    val previousCapacity = AdvancedSettings.getInt(capacityKey)
    val previousLines = AdvancedSettings.getInt(linesKey)
    try {
      AdvancedSettings.setInt(capacityKey, capacityKiB)
      AdvancedSettings.setInt(linesKey, historyRows)
      val workload = logWorkload(lines)
      println("PAYLOAD: $lines lines, ${workload.bytes} bytes; retention $capacityKiB KiB, $historyRows JediTerm rows")
      if (lines == 30_685) assertThat(workload.bytes).isEqualTo(5_243_010)
      compare(Endpoint.PAIRED_HISTORY, listOf(workload))
    }
    finally {
      AdvancedSettings.setInt(capacityKey, previousCapacity)
      AdvancedSettings.setInt(linesKey, previousLines)
    }
  }

  private suspend fun CoroutineScope.compare(endpoint: Endpoint, workloads: List<Workload> = workloads(endpoint)) {
    val available = TerminalEmulatorType.entries.filter { endpoint != Endpoint.CONTENT || it != TerminalEmulatorType.KetraTerm }
    val emulators = selectedBenchmarkBackends(available) { it.name }
    val harnesses = LinkedHashMap<TerminalEmulatorType, Harness>()
    val recording = System.getenv("TERMINAL_BENCHMARK_PROFILE_DIR")?.let {
      Files.createDirectories(Path.of(it))
      Recording(Configuration.getConfiguration("profile"))
    }
    try {
      for (emulator in emulators) harnesses[emulator] = createHarness(emulator, endpoint)
      recording?.start()
      val report = StringBuilder()
      report.appendLine("Reused sessions: input to ${endpoint.name.lowercase()}, ${COLUMNS}x$ROWS")
      report.appendLine("Each backend uses one session across all workloads.")
      report.appendLine("Pre-input idle: $benchmarkPreInputIdleMillis ms, outside timing; workload condition only.")
      report.appendLine("$MEASURED_RUNS measured runs follow $WARMUP_RUNS warmup runs per workload.")
      report.appendLine("Backend order rotates. Screen and history clearing finish outside the timed interval.")
      report.appendLine("Marker alone does not prove deferred history is ready. History endpoints verify exact retained text after timing.")
      report.appendLine("Text export for verification, painting, and screen presentation are excluded.")
      report.appendLine("workload | size(KB) | " + emulators.joinToString(" | ") { it.name + " p50 ms" })
      if (endpoint == Endpoint.PAIRED_HISTORY) {
        report.appendLine("Marker: output available to the frontend on the UI thread. KetraTerm reads the native frame.")
        report.appendLine("History ready: the frontend has all retained output in its required model. Verification is outside timing.")
        report.appendLine("Document backends include required projection. KetraTerm uses retained native rows at the marker.")
        report.appendLine("No extra native observer is attached to document-enabled backends. This is an integration comparison.")
      }
      var sequence = 0
      for (workload in workloads) {
        println("REUSED START ${endpoint.name} ${workload.name}")
        val samples = emulators.associateWith { LongArray(MEASURED_RUNS) }
        val visibleSamples = emulators.associateWith { LongArray(MEASURED_RUNS) }
        repeat(WARMUP_RUNS + MEASURED_RUNS) { run ->
          for (index in emulators.indices) {
            val emulator = emulators[(index + run) % emulators.size]
            val harness = harnesses.getValue(emulator)
            val nanos = withTimeout(30.seconds) {
              harness.clear(sequence++)
              if (workload.prelude.isNotEmpty()) harness.submit(workload.prelude, "REUSEPRE${sequence++}")
              awaitBenchmarkInputGap()
              val token = "REUSEDONE${sequence++}"
              val expected = if (endpoint.verifiesHistory) workload.text + "\n$token" else null
              harness.submit(workload.payload, token, expected)
            }
            if (run >= WARMUP_RUNS) {
              samples.getValue(emulator)[run - WARMUP_RUNS] = nanos
              visibleSamples.getValue(emulator)[run - WARMUP_RUNS] = harness.visibleNanos
            }
          }
        }
        val medians = emulators.map { emulator ->
          if (endpoint.verifiesHistory) {
            val visible = visibleSamples.getValue(emulator)
            println("PAIRED VISIBLE ${emulator.name} ${workload.name}: " +
                    visible.joinToString { "%.3f".format(it / 1_000_000.0) } + " ms")
            val visibleMedian = visible.sorted()[MEASURED_RUNS / 2] / 1_000_000.0
            println("PAIRED VISIBLE P50 ${emulator.name} ${workload.name}: %.3f ms".format(visibleMedian))
          }
          val values = samples.getValue(emulator)
          assertThat(values.all { it > 0 }).isTrue()
          println("REUSED SAMPLES ${endpoint.name} ${emulator.name} ${workload.name}: " +
                  values.joinToString { "%.3f".format(it / 1_000_000.0) } + " ms")
          values.sort()
          println("REUSED MAX ${endpoint.name} ${emulator.name} ${workload.name}: %.3f ms".format(values.last() / 1_000_000.0))
          values[MEASURED_RUNS / 2] / 1_000_000.0
        }
        report.appendLine(workload.name + " | %.1f | ".format(workload.bytes / 1024.0) +
                          medians.joinToString(" | ") { "%.2f".format(it) })
      }
      println(report)
      println("SESSION COUNT ${endpoint.name}: ${harnesses.size}; no session is recreated between samples or workloads.")
    }
    finally {
      withContext(NonCancellable) {
        for (harness in harnesses.values) harness.close()
        recording?.let {
          it.stop()
          val file = Path.of(System.getenv("TERMINAL_BENCHMARK_PROFILE_DIR")).resolve("reused-${endpoint.name.lowercase()}.jfr")
          it.dump(file)
          it.close()
          println("Allocation profile: $file; includes warmup, clearing, validation, and disposal.")
        }
      }
    }
  }

  private suspend fun CoroutineScope.createHarness(emulator: TerminalEmulatorType, endpoint: Endpoint): Harness {
    val scope = childScope("Reused benchmark $emulator", Dispatchers.Default)
    val (session, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(
      projectFixture.get(), scope, emulatorType = emulator, isLowLevelSession = endpoint == Endpoint.CONTENT,
    )
    val harness = Harness(scope, connector, emulator == TerminalEmulatorType.KetraTerm)
    try {
      if (endpoint == Endpoint.CONTENT) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
          session.getOutputFlow().collect { batch ->
            for (event in batch) {
              if (event is TerminalContentUpdatedEvent) harness.observe(event.text)
            }
          }
        }
      }
      else withContext(Dispatchers.EDT) {
        val settings = JBTerminalSystemSettingsProvider()
        val view: TerminalView = if (emulator == TerminalEmulatorType.KetraTerm) {
          KetraTermTerminalView(projectFixture.get(), settings, scope)
        }
        else TerminalViewImpl(projectFixture.get(), settings, null, scope)
        if (view is TerminalViewImpl) {
          harness.copyRetainedText = {
            val model = view.outputModels.regular
            model.getText(model.startOffset, model.endOffset).toString()
          }
        }
        if (view is TerminalViewImpl) view.outputModels.regular.addListener(scope.asDisposable(), object : TerminalOutputModelListener {
          override fun afterContentChanged(event: TerminalContentChangeEvent) {
            val model = view.outputModels.regular
            val probe = harness.probe.get() ?: return
            if (probe.arrival.isCompleted) return
            val time = System.nanoTime()
            if (probe.token == null) {
              if (model.getText(model.startOffset, model.endOffset).isBlank()) probe.arrival.complete(time)
            }
            else {
              val line = model.lastLineIndex
              val text = if (probe.wholeModel) {
                model.getText(model.startOffset, model.endOffset)
              }
              else model.getText(model.getStartOfLine(line), model.getEndOfLine(line))
              if (text.contains(probe.token)) {
                probe.visibleArrival.complete(if (endpoint == Endpoint.PAIRED_HISTORY) System.nanoTime() else time)
              }
              if (text.contains(probe.token) && (probe.expected == null || model.textLength == probe.expected.length)) {
                probe.arrival.complete(if (endpoint == Endpoint.PAIRED_HISTORY) System.nanoTime() else time)
              }
            }
          }
        })
        when (view) {
          is KetraTermTerminalView -> {
            view.connectToSession(session)
            configureKetraTermBenchmarkGrid(view.terminal, session as KetraTermTerminalSession, COLUMNS, ROWS)
          }
          is TerminalViewImpl -> view.connectToSession(session)
        }
        if (session is KetraTermTerminalSession) {
          harness.copyRetainedText = session::retainedText
          scope.launch(Dispatchers.UI, start = CoroutineStart.UNDISPATCHED) {
            session.terminalSession.renderGeneration.collect { harness.observeNative(session) }
          }
        }
      }
      return harness
    }
    catch (error: Throwable) {
      com.intellij.diagnostic.rethrowControlFlowException(error)
      withContext(NonCancellable) { harness.close() }
      throw error
    }
  }

  private class Probe(val token: String?, val expected: String? = null, val wholeModel: Boolean = false) {
    val arrival = CompletableDeferred<Long>()
    val visibleArrival = CompletableDeferred<Long>()
  }

  private class Harness(
    private val scope: CoroutineScope,
    private val connector: LoopbackTtyConnector,
    private val native: Boolean,
  ) {
    private var codes = if (native) IntArray(COLUMNS) else IntArray(0)
    private var flags = if (native) IntArray(COLUMNS) else IntArray(0)
    var copyRetainedText: (() -> String)? = null
    val probe = AtomicReference<Probe?>()
    var visibleNanos: Long = 0
      private set

    fun observe(text: CharSequence) {
      val current = probe.get() ?: return
      if (current.token?.let { text.contains(it) } ?: text.isBlank()) current.arrival.complete(System.nanoTime())
    }

    fun observeNative(session: KetraTermTerminalSession) {
      check(native)
      val current = probe.get() ?: return
      if (current.arrival.isCompleted) return
      val token = current.token
      if (token == null) {
        // Clearing is outside the measured workload.
        if (requireNotNull(copyRetainedText).invoke().isBlank()) current.arrival.complete(System.nanoTime())
        return
      }
      var readyAt = 0L
      session.terminalSession.readRenderFrame { frame ->
        if (codes.size < frame.columns) {
          codes = IntArray(frame.columns)
          flags = IntArray(frame.columns)
        }
        frame.copyTextLine(frame.cursor.row, codes, flags = flags)
        var found = frame.cursor.column == token.length
        for (index in token.indices) if (codes[index] != token[index].code) found = false
        if (found) readyAt = System.nanoTime()
      }
      if (readyAt == 0L) return
      // The final marker follows all input. Retention is verified after this readiness timestamp.
      current.visibleArrival.complete(readyAt)
      current.arrival.complete(readyAt)
    }

    suspend fun clear(sequence: Int) {
      submit(CLEAR, "REUSECLEAR$sequence", wholeModel = true)
      val cleared = Probe(null)
      probe.set(cleared)
      connector.feed(CLEAR)
      cleared.arrival.await()
      probe.set(null)
    }

    suspend fun submit(payload: String, token: String, expected: String? = null, wholeModel: Boolean = false): Long {
      val current = Probe(token, expected, wholeModel)
      val marker = "\r\n$token"
      probe.set(current)
      val start = System.nanoTime()
      connector.feed(payload)
      connector.feed(marker)
      val elapsed = current.arrival.await() - start
      visibleNanos = if (expected != null) current.visibleArrival.await() - start else elapsed
      if (expected != null) {
        // No more input is submitted until the retained model has been checked.
        val actual = withContext(Dispatchers.UI) { requireNotNull(copyRetainedText).invoke() }
        assertThat(actual).isEqualTo(expected)
      }
      probe.set(null)
      return elapsed
    }

    suspend fun close() {
      scope.coroutineContext.job.cancel()
      connector.close()
      scope.coroutineContext.job.join()
    }
  }

  private enum class Endpoint(val verifiesHistory: Boolean = false) {
    CONTENT, FRONTEND, COMPLETE_HISTORY(true), PAIRED_HISTORY(true),
  }
  private class Workload(val name: String, val payload: String, val prelude: String = "") {
    val bytes = payload.toByteArray(Charsets.UTF_8).size
    val text = payload.replace("\r\n", "\n")
  }

  private fun workloads(endpoint: Endpoint): List<Workload> {
    if (endpoint == Endpoint.PAIRED_HISTORY) return listOf(logWorkload(6_000))
    val plain = Workload("plain-text-scroll", buildString {
      repeat(1_300) { append("The quick brown fox jumps over the lazy dog 0123456789 abcdefghijklmnopqrstuvwx\r\n") }
    })
    val unicode = Workload("unicode-wide", buildString {
      repeat(1_200) { append("你好世界 こんにちは 안녕하세요 😀🎉🚀 mixed-ascii-0123456789\r\n") }
    })
    if (endpoint.verifiesHistory) return listOf(plain, logWorkload(6_000), unicode)
    val cursor = buildString {
      repeat(10_000) { index -> append("\u001b[${index % ROWS + 1};${index % COLUMNS + 1}H*") }
      append("\u001b[$ROWS;1H")
    }
    return listOf(plain, logWorkload(6_000), Workload("single-long-line", buildString {
      while (length < 128 * 1024) append("wrap-me-0123456789-abcdefghijklmnopqrstuvwxyz-")
    }), Workload("sgr-colored-cells", buildString {
      repeat(6_000) { index ->
        append("\u001b[38;5;${index % 256}mcell")
        if (index % 12 == 11) append("\r\n")
      }
      append("\u001b[0m\r\n")
    }), Workload(CURSOR_BURST_NAME, cursor), unicode, Workload("retained-history", cursor, buildString {
      repeat(6_000) { append("history $it short line 0123456789\r\n") }
    }))
  }

  private fun logWorkload(lines: Int): Workload = Workload(if (lines == 1_000) "log-1000-lines" else "log-file-cat", buildString {
    repeat(lines) { index ->
      append("2026-08-07 12:34:56,").append("%03d".format(index % 1000))
      append(" [").append("%6d".format(index)).append("] INFO - #c.i.o.diagnostic.ExampleComponent - ")
      append("request ").append(index % 10_000).append(" handled in 12 ms, state=RUNNING, payload: ")
      append("abcdefghijklmnopqrstuvwxyz-0123456789\r\n")
    }
  })

  companion object {
    private val projectFixture = projectFixture()
    private const val COLUMNS = 80
    private const val ROWS = 24
    private const val WARMUP_RUNS = 5
    private const val MEASURED_RUNS = 9
    private const val CLEAR = "\u001b[0m\u001b[3J\u001b[2J\u001b[H"
  }
}
