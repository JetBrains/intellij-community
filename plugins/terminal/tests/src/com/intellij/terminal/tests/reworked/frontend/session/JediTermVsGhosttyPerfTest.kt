// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session

import com.intellij.openapi.application.EDT
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.impl.TerminalViewImpl
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.asDisposable
import jdk.jfr.Configuration
import jdk.jfr.Recording
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Compares the three emulators through the [TerminalSession] output path.
 * Measures the time from input to a content event on [TerminalSession.getOutputFlow].
 * A second endpoint includes application to the real frontend model on the EDT.
 * A third endpoint verifies complete history after its application.
 * All endpoints exclude painting and screen presentation.
 * Each run uses a new session, so earlier output cannot change the next run.
 * The test reports the median and the slowest run after warmup.
 */
@RunWith(JUnit4::class)
internal class JediTermVsGhosttyPerfTest {
  private val projectRule: ProjectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @Rule
  @JvmField
  val ruleChain: RuleChain = RuleChain(projectRule, disposableRule)

  @Test
  fun `compare terminal backend content event latency`() = timeoutRunBlocking(5.minutes) {
    compare(frontend = false)
  }

  @Test
  fun `compare terminal backend frontend application latency`() = timeoutRunBlocking(5.minutes) {
    compare(frontend = true)
  }

  @Test
  fun `compare terminal backend complete history application latency`() = timeoutRunBlocking(5.minutes) {
    compare(frontend = true, completeHistory = true)
  }

  private suspend fun CoroutineScope.compare(frontend: Boolean, completeHistory: Boolean = false) {
    val workloads = if (completeHistory) listOf(
      Workload("plain-text-scroll", plainTextWorkload()),
      Workload("log-1000-lines", logFileCatWorkload(1_000)),
      Workload("unicode-wide", unicodeWorkload()),
    )
    else listOf(
      Workload("plain-text-scroll", plainTextWorkload()),
      Workload("log-file-cat", logFileCatWorkload()),
      Workload("single-long-line", singleLongLineWorkload()),
      Workload("sgr-colored-cells", sgrColoredWorkload()),
      Workload(CURSOR_BURST_NAME, cursorMotionWorkload()),
      Workload("unicode-wide", unicodeWorkload()),
      Workload("retained-history", cursorMotionWorkload(), retainedHistoryPrelude()),
    )

    val profileDirectory = System.getenv("TERMINAL_BENCHMARK_PROFILE_DIR")?.let { Files.createDirectories(Path.of(it)) }
    val recording = profileDirectory?.let { Recording(Configuration.getConfiguration("profile")) }
    recording?.start()
    try {
      println("Pre-input idle: $benchmarkPreInputIdleMillis ms, outside timing; workload condition only.")
      val emulators = selectedBenchmarkBackends(listOf(TerminalEmulatorType.JediTerm, TerminalEmulatorType.Ghostty)) { it.name }
      val results = emulators.associateWith { LinkedHashMap<String, Measurement>() }
      for (workload in workloads) {
        val samples = emulators.associateWith { ArrayList<Sample>(MEASURED_RUNS) }
        repeat(WARMUP_RUNS + MEASURED_RUNS) { run ->
          for (index in emulators.indices) {
            val emulator = emulators[(index + run) % emulators.size]
            val elapsed = withTimeout(30.seconds) {
              if (frontend) sendAndAwaitFrontend(emulator, workload, markerToken(run), completeHistory)
              else sendAndAwait(emulator, workload, markerToken(run))
            }
            if (run >= WARMUP_RUNS) samples.getValue(emulator).add(elapsed)
          }
        }
        for (emulator in emulators) {
          val values = samples.getValue(emulator)
          values.sortBy { it.nanos }
          results.getValue(emulator)[workload.name] = Measurement(values[MEASURED_RUNS / 2].nanos, values.last().nanos)
          println("SAMPLES ${emulator.name} ${workload.name}: ${values.joinToString { "%.3f".format(it.nanos / 1_000_000.0) }} ms")
          if (!frontend) {
            val projections = values.joinToString { "${it.projectedChars}/${it.contentEvents}" }
            println("PROJECTION ${emulator.name} ${workload.name}: $projections chars/events")
          }
        }
      }

      val report = StringBuilder()
      report.appendLine()
      val endpoint = when {
        completeHistory -> "complete frontend history"
        frontend -> "frontend marker application"
        else -> "content marker event"
      }
      report.appendLine("Input to $endpoint ($MEASURED_RUNS runs after $WARMUP_RUNS warmup, ${COLUMNS}x$ROWS):")
      report.appendLine("Backend order rotates for each run. Each run uses a new session.")
      if (completeHistory) {
        report.appendLine("All output fits each history limit. The test verifies the complete text after recording the arrival time.")
      }
      else {
        report.appendLine("Retained-history preloads $HISTORY_LINES lines at the same endpoint. Time and size exclude this prelude.")
        report.appendLine("Marker arrival can precede deferred history restoration. It does not establish complete output application.")
      }
      report.appendLine("workload | size(KiB) | " + emulators.joinToString(" | ") { "${it.name} p50/max ms" })
      for (workload in workloads) {
        val measurements = emulators.map { results.getValue(it).getValue(workload.name) }
        measurements.forEach { assertThat(it.medianNanos).describedAs(workload.name).isPositive() }
        report.appendLine("%s | %.1f | ".format(workload.name, workload.payload.toByteArray(Charsets.UTF_8).size / 1024.0) +
                          measurements.joinToString(" | ") { "%.2f/%.2f".format(it.medianNanos / 1_000_000.0, it.maxNanos / 1_000_000.0) })
      }
      // Print the result with the test output.
      println(report)
    }
    finally {
      recording?.let {
        it.stop()
        val name = when {
          completeHistory -> "complete-history.jfr"
          frontend -> "frontend-model.jfr"
          else -> "content-event.jfr"
        }
        val file = profileDirectory!!.resolve(name)
        it.dump(file)
        it.close()
        println("Allocation profile: $file")
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Measurement
  // ---------------------------------------------------------------------------

  private class Workload(val name: String, val payload: String, val prelude: String = "")
  private data class Sample(val nanos: Long, val projectedChars: Long, val contentEvents: Int)
  private data class Measurement(val medianNanos: Long, val maxNanos: Long)

  /**
   * Measures when the content event with [token] reaches the output flow collector.
   */
  private suspend fun CoroutineScope.sendAndAwait(
    emulatorType: TerminalEmulatorType,
    workload: Workload,
    token: String,
  ): Sample {
    // The read loop blocks on its own dispatcher between inputs.
    val sessionScope = childScope("JediTermVsGhosttyPerfTest-$emulatorType", Dispatchers.Default)
    val (session, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(
      project = projectRule.project,
      coroutineScope = sessionScope,
      emulatorType = emulatorType,
      isLowLevelSession = true,
    )
    try {
      val arrival = CompletableDeferred<Sample>()
      val preludeArrival = if (workload.prelude.isNotEmpty()) CompletableDeferred<Sample>() else null
      val preludeToken = preludeMarkerToken(token)
      var projectedChars = 0L
      var contentEvents = 0
      sessionScope.launch(start = CoroutineStart.UNDISPATCHED) {
        session.getOutputFlow().collect { batch ->
          for (event in batch) {
            if (event is TerminalContentUpdatedEvent) {
              projectedChars += event.text.length
              contentEvents++
              if (preludeArrival != null && !preludeArrival.isCompleted && event.text.contains(preludeToken)) {
                preludeArrival.complete(Sample(System.nanoTime(), projectedChars, contentEvents))
              }
              if (event.text.contains(token)) arrival.complete(Sample(System.nanoTime(), projectedChars, contentEvents))
            }
          }
        }
      }
      val ready = CompletableDeferred<Unit>()
      connector.responseHandler = { ready.complete(Unit) }
      connector.feed("\u001B[6n")
      ready.await()
      connector.responseHandler = null
      val baseline = if (preludeArrival != null) {
        connector.feed(workload.prelude)
        connector.feed("\r\n$preludeToken")
        preludeArrival.await()
      }
      else Sample(0, 0, 0)
      awaitBenchmarkInputGap()
      val start = System.nanoTime()
      connector.feed(workload.payload)
      connector.feed("\r\n$token")
      return arrival.await().let {
        Sample(it.nanos - start, it.projectedChars - baseline.projectedChars, it.contentEvents - baseline.contentEvents)
      }
    }
    finally {
      withContext(NonCancellable) {
        connector.close()
        sessionScope.coroutineContext.job.cancelAndJoin()
      }
    }
  }

  /** Measures connector input through the real view's model listener on the EDT. Painting is excluded. */
  private suspend fun CoroutineScope.sendAndAwaitFrontend(
    emulatorType: TerminalEmulatorType,
    workload: Workload,
    token: String,
    completeHistory: Boolean,
  ): Sample {
    val scope = childScope("Frontend benchmark $emulatorType", Dispatchers.Default)
    val (session, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(
      projectRule.project, scope, emulatorType = emulatorType,
    )
    try {
      val expectedText = if (completeHistory) workload.payload.replace("\r\n", "\n") + "\n$token" else null
      val completeText = if (completeHistory) CompletableDeferred<CharSequence>() else null
      val arrival = CompletableDeferred<Long>()
      val preludeArrival = if (workload.prelude.isNotEmpty()) CompletableDeferred<Unit>() else null
      val preludeToken = preludeMarkerToken(token)
      withContext(Dispatchers.EDT) {
        val settings = JBTerminalSystemSettingsProvider()
        val view = TerminalViewImpl(projectRule.project, settings, null, scope)
        view.outputModels.regular.addListener(scope.asDisposable(), object : TerminalOutputModelListener {
          override fun afterContentChanged(event: TerminalContentChangeEvent) {
            val model = view.outputModels.regular
            val lastLine = model.lastLineIndex
            val text = model.getText(model.getStartOfLine(lastLine), model.getEndOfLine(lastLine))
            if (preludeArrival != null && !preludeArrival.isCompleted && text.contains(preludeToken)) {
              preludeArrival.complete(Unit)
            }
            if (text.contains(token) && (expectedText == null || model.textLength == expectedText.length)) {
              arrival.complete(System.nanoTime())
              completeText?.complete(model.getText(model.startOffset, model.endOffset))
            }
          }
        })
        view.connectToSession(session)
      }
      val ready = CompletableDeferred<Unit>()
      connector.responseHandler = { ready.complete(Unit) }
      connector.feed("\u001b[6n")
      ready.await()
      connector.responseHandler = null
      if (preludeArrival != null) {
        connector.feed(workload.prelude)
        connector.feed("\r\n$preludeToken")
        preludeArrival.await()
      }
      awaitBenchmarkInputGap()
      val start = System.nanoTime()
      connector.feed(workload.payload)
      connector.feed("\r\n$token")
      val elapsed = arrival.await() - start
      if (completeText != null) assertThat(completeText.await().toString()).isEqualTo(expectedText)
      return Sample(elapsed, 0, 0)
    }
    finally {
      withContext(NonCancellable) {
        connector.close()
        scope.coroutineContext.job.cancelAndJoin()
      }
    }
  }

  /** A unique completion marker that fits on one line. */
  private fun markerToken(seq: Int): String = "PERFDONE$seq"

  private fun preludeMarkerToken(token: String): String = "PERFREADY${token.removePrefix("PERFDONE")}"

  // ---------------------------------------------------------------------------
  // Workloads (payloads only; the harness appends a unique completion marker per send)
  // ---------------------------------------------------------------------------

  /** A large plain-ASCII dump that scrolls the screen; every line fits the width, so nothing soft-wraps. */
  private fun plainTextWorkload(): String = buildString {
    val line = "The quick brown fox jumps over the lazy dog 0123456789 abcdefghijklmnopqrstuvwx"
    repeat(1_300) { append(line).append("\r\n") }
  }

  /**
   * `cat`-ing an IDE log: ~1 MB of ~170-character lines (the average of a real idea.log), so at
   * 80 columns every line soft-wraps into 2-3 rows. Wrapping is what separates this from
   * [plainTextWorkload]: an incremental content update cannot start mid-line, so a wrapped line
   * straddling the emitted/unemitted boundary is backed up to its first row and re-emitted whole.
   */
  private fun logFileCatWorkload(lines: Int = 6_000): String = buildString {
    repeat(lines) { i ->
      append("2026-08-07 12:34:56,").append("%03d".format(i % 1000))
      append(" [").append("%6d".format(i)).append("] INFO - #c.i.o.diagnostic.ExampleComponent - ")
      append("request ").append(i % 10_000).append(" handled in 12 ms, state=RUNNING, payload: ")
      append("abcdefghijklmnopqrstuvwxyz-0123456789")
      append("\r\n")
    }
  }

  /**
   * One 128 KB logical line without a line break.
   * Each projection must include the complete wrapped line. The report records the projected character count.
   */
  private fun singleLongLineWorkload(): String = buildString {
    val chunk = "wrap-me-0123456789-abcdefghijklmnopqrstuvwxyz-"
    while (length < 128 * 1024) append(chunk)
  }

  /** Dense styled cells: a fresh 256-color SGR before every short token. */
  private fun sgrColoredWorkload(): String = buildString {
    repeat(6_000) { i ->
      append(ESC).append("[38;5;").append(i % 256).append('m').append("cell")
      if (i % 12 == 11) append("\r\n")
    }
    append(ESC).append("[0m\r\n")
  }

  /** Heavy cursor motion: absolute positioning all over the grid, then a single glyph. */
  private fun cursorMotionWorkload(): String = buildString {
    repeat(10_000) { i ->
      val row = (i % ROWS) + 1
      val column = (i % COLUMNS) + 1
      append(ESC).append('[').append(row).append(';').append(column).append('H').append('*')
    }
    // Park on the last row so the trailing marker lands predictably at the bottom.
    append(ESC).append('[').append(ROWS).append(";1H")
  }

  /** Wide/supplementary Unicode mixed with ASCII. */
  private fun unicodeWorkload(): String = buildString {
    val line = "你好世界 こんにちは 안녕하세요 😀🎉🚀 mixed-ascii-0123456789"
    repeat(1_200) { append(line).append("\r\n") }
  }

  /** Keeps short lines in history before the timed cursor updates. */
  private fun retainedHistoryPrelude(): String = buildString {
    repeat(HISTORY_LINES) { index ->
      append("history ").append(index).append(" short line 0123456789\r\n")
    }
  }

  companion object {
    private const val COLUMNS = 80
    private const val ROWS = 24
    private const val WARMUP_RUNS = 2
    private const val MEASURED_RUNS = 9
    private const val HISTORY_LINES = 6_000

    private val ESC: String = Char(0x1B).toString()
  }
}
