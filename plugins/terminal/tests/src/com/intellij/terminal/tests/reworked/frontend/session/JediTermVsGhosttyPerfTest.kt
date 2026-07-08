// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session

import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.tests.reworked.util.LoopbackTtyConnector
import com.intellij.terminal.tests.reworked.util.TerminalOutputEventCollector
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil
import com.intellij.terminal.tests.reworked.util.awaitEvent
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.time.Duration.Companion.minutes

/**
 * Benchmark comparing the two terminal emulators **through the production [TerminalSession] machinery**, so the numbers
 * reflect real usage rather than a raw parser micro-benchmark:
 * - **jediterm** — the default `createTerminalSession` backend;
 * - **ghostty** — the same pipeline with the `terminal.use.ghostty.emulator` registry key enabled (`libghostty-vt`).
 *
 * Both emulators are driven exactly like a shell would drive them: raw VT bytes are fed to a [LoopbackTtyConnector]
 * and the resulting [org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent]s are observed via
 * [TerminalSession.getOutputFlow]. So the timing includes the read loop, the emulator, the diff-into-events step, and
 * the event flow — everything the frontend depends on except rendering.
 *
 * Methodology: for each emulator a **single session instance** is created and reused for every workload and every
 * repetition (a fresh instance per run would hide steady-state costs and defeat JIT warmup). Each workload's identical
 * byte payload is re-sent several times; a send is considered complete when its unique trailing marker shows up in a
 * content event. The best (minimum) of the measured repetitions is reported.
 *
 * This is a comparison/benchmark rather than a pass/fail test: it asserts only that both emulators actually processed
 * each workload, and prints the timings.
 */
@RunWith(JUnit4::class)
internal class JediTermVsGhosttyPerfTest {
  private val projectRule: ProjectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @Rule
  @JvmField
  val ruleChain: RuleChain = RuleChain(projectRule, disposableRule)

  @Test
  fun `compare JediTerm and Ghostty throughput through the session pipeline`() = timeoutRunBlocking(3.minutes) {
    val workloads = listOf(
      Workload("plain-text-scroll", plainTextWorkload()),
      Workload("sgr-colored-cells", sgrColoredWorkload()),
      Workload("cursor-motion", cursorMotionWorkload()),
      Workload("unicode-wide", unicodeWorkload()),
    )

    // One session per emulator, reused across all workloads and repetitions.
    val jediTerm = measureEmulator(TerminalEmulatorType.JediTerm, workloads)
    val ghostty = measureEmulator(TerminalEmulatorType.Ghostty, workloads)

    val report = StringBuilder()
    report.appendLine()
    report.appendLine("Terminal emulator throughput via TerminalSession (best of $MEASURED_RUNS after $WARMUP_RUNS warmup, ${COLUMNS}x$ROWS):")
    // "gh/jt" is Ghostty time divided by JediTerm time: >1 means Ghostty is that many times slower, <1 means faster.
    report.appendLine("%-20s %10s %12s %12s %10s".format(
      "workload", "size(KB)", "jediterm ms", "ghostty ms", "gh/jt"))

    for (workload in workloads) {
      val sizeBytes = workload.payload.toByteArray(Charsets.UTF_8).size
      val jt = jediTerm.getValue(workload.name)
      val gh = ghostty.getValue(workload.name)
      // Sanity: both emulators actually processed the workload (its completion marker was observed).
      assertThat(jt).describedAs("JediTerm did not process '${workload.name}'").isPositive()
      assertThat(gh).describedAs("Ghostty did not process '${workload.name}'").isPositive()
      report.appendLine("%-20s %10.1f %12.2f %12.2f %9.1fx".format(
        workload.name,
        sizeBytes / 1024.0,
        jt / 1_000_000.0,
        gh / 1_000_000.0,
        gh.toDouble() / jt,
      ))
    }

    // A benchmark table is only useful when it is actually visible, so print it to stdout (the logger would bury it in idea.log).
    println(report)
  }

  // ---------------------------------------------------------------------------
  // Measurement
  // ---------------------------------------------------------------------------

  private class Workload(val name: String, val payload: String)

  /**
   * Creates a single [TerminalSession] for [emulatorType] and measures every workload on it, returning the best
   * (minimum) processing time per workload name.
   */
  private suspend fun CoroutineScope.measureEmulator(emulatorType: TerminalEmulatorType, workloads: List<Workload>): Map<String, Long> {
    emulatorType.setDefault(disposableRule.disposable)
    // Not the timeoutRunBlocking dispatcher: the session's read loop blocks its thread between
    // payloads, so it needs its own threads — on the test's runBlocking thread it would starve
    // everything else.
    val sessionScope = childScope("JediTermVsGhosttyPerfTest-$emulatorType", Dispatchers.Default)
    try {
      val (session, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(projectRule.project, sessionScope)
      val collector = TerminalOutputEventCollector(session, sessionScope)

      var markerSeq = 0
      val results = LinkedHashMap<String, Long>()
      for (workload in workloads) {
        repeat(WARMUP_RUNS) {
          sendAndAwait(connector, collector, workload.payload, markerToken(markerSeq++))
        }
        var best = Long.MAX_VALUE
        repeat(MEASURED_RUNS) {
          best = minOf(best, sendAndAwait(connector, collector, workload.payload, markerToken(markerSeq++)))
        }
        results[workload.name] = best
      }
      return results
    }
    finally {
      sessionScope.cancel()
    }
  }

  /**
   * Writes [payload] followed by a unique [token] on its own line, then waits until that token appears in a content
   * event — i.e. until the session has fully processed the payload. Returns the elapsed nanoseconds.
   */
  private suspend fun sendAndAwait(
    connector: LoopbackTtyConnector,
    collector: TerminalOutputEventCollector,
    payload: String,
    token: String,
  ): Long {
    val start = System.nanoTime()
    connector.feed(payload)
    connector.feed("\r\n$token")
    collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains(token) }
    return System.nanoTime() - start
  }

  /** Unique, control-char-free completion marker; kept short so it fits on a single line. */
  private fun markerToken(seq: Int): String = "PERFDONE$seq"

  // ---------------------------------------------------------------------------
  // Workloads (payloads only; the harness appends a unique completion marker per send)
  // ---------------------------------------------------------------------------

  /** A large plain-ASCII dump that scrolls the screen (like `cat`-ing a big log). */
  private fun plainTextWorkload(): String = buildString {
    val line = "The quick brown fox jumps over the lazy dog 0123456789 abcdefghijklmnopqrstuvwx"
    repeat(1_300) { append(line).append("\r\n") }
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

  companion object {
    private const val COLUMNS = 80
    private const val ROWS = 24
    private const val WARMUP_RUNS = 2
    private const val MEASURED_RUNS = 5

    private val ESC: String = Char(0x1B).toString()
  }
}
