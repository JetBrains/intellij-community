// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session

import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import com.intellij.terminal.tests.reworked.frontend.session.TerminalRenderingBenchmark.PaintHarness
import com.intellij.terminal.tests.reworked.frontend.session.TerminalRenderingBenchmark.PaintObserver
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import io.github.ketraterm.ui.swing.api.SwingTerminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Cursor
import java.awt.Rectangle
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.RepaintManager
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Measures the production renderers with fresh and reused sessions.
 * A unique background color proves that each completed paint contains the new output.
 * Images and graphics are reused. Software painting excludes display presentation.
 */
@TestApplication
internal class TerminalRenderingPerfTest {
  @Test
  fun `visible file link becomes actionable during history discovery`(@TempDir directory: Path): Unit = timeoutRunBlocking(3.minutes) {
    val file = Files.createFile(directory.resolve("README"))
    checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file))
    val benchmark = TerminalRenderingBenchmark(projectFixture.get())
    val observer = PaintObserver()
    val previous = withContext(Dispatchers.EDT) {
      RepaintManager.currentManager(null).also { RepaintManager.setCurrentManager(observer) }
    }
    try {
      val harness = benchmark.createHarness(this, TerminalEmulatorType.KetraTerm, observer)
      try {
        val terminal = harness.component as SwingTerminal
        val input = buildString {
          repeat(6_000) { append("history $it short line 0123456789\r\n") }
          append('"').append(file).append('"')
        }
        val modifier = if (SystemInfoRt.isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
        val samples = LongArray(MEASURED_RUNS)
        val afterPaint = LongArray(MEASURED_RUNS)
        var readyAtFirstCheck = 0
        var sequence = 40_000
        repeat(WARMUP_RUNS + MEASURED_RUNS) { run ->
          harness.submit(CLEAR, sequence++)
          val bounds = Rectangle()
          withContext(Dispatchers.EDT) {
            check(terminal.copyCellBounds(0, TerminalRenderingBenchmark.ROWS - 2, bounds))
            terminal.dispatchEvent(MouseEvent(terminal, MouseEvent.MOUSE_MOVED, 0, modifier,
                                              bounds.x + bounds.width / 2, bounds.y + bounds.height / 2, 0, false))
            check(terminal.cursor.type != Cursor.HAND_CURSOR)
          }
          val start = System.nanoTime()
          harness.submit(input, sequence++)
          val returnedFromPaint = System.nanoTime()
          if (withContext(Dispatchers.EDT) { terminal.cursor.type == Cursor.HAND_CURSOR }) {
            if (run >= WARMUP_RUNS) readyAtFirstCheck++
          }
          else {
            withTimeout(10.seconds) {
              do { delay(1) } while (!withContext(Dispatchers.EDT) { terminal.cursor.type == Cursor.HAND_CURSOR })
            }
          }
          if (run >= WARMUP_RUNS) {
            val ready = System.nanoTime()
            samples[run - WARMUP_RUNS] = ready - start
            afterPaint[run - WARMUP_RUNS] = ready - returnedFromPaint
          }
        }
        println("VISIBLE FILE LINK submission-to-paint-and-modifier-hover-nanos: " + samples.joinToString())
        println("VISIBLE FILE LINK wait after paint returned in nanos: " + afterPaint.joinToString())
        println("VISIBLE FILE LINK ready at first check after paint: $readyAtFirstCheck/$MEASURED_RUNS")
      }
      finally { withContext(NonCancellable) { harness.close() } }
    }
    finally { withContext(NonCancellable + Dispatchers.EDT) { RepaintManager.setCurrentManager(previous) } }
  }

  private val configuredBackends = selectedBenchmarkBackends(TerminalEmulatorType.entries) { it.name }

  @Test
  fun `compare interactive terminal updates`(): Unit = timeoutRunBlocking(5.minutes) {
    val benchmark = TerminalRenderingBenchmark(projectFixture.get())
    val observer = PaintObserver()
    val previous = withContext(Dispatchers.EDT) {
      RepaintManager.currentManager(null).also { RepaintManager.setCurrentManager(observer) }
    }
    val backends = configuredBackends
    val harnesses = LinkedHashMap<TerminalEmulatorType, PaintHarness>()
    var sequence = 50_000
    try {
      for (backend in backends) harnesses[backend] = benchmark.createHarness(this, backend, observer)
      val prompt = "$ git status"
      val cases = listOf(
        Interaction("cursor-left-one-cell", prompt, "\u001b[D", prompt, cursorColumn = prompt.length - 1),
        Interaction("echo-one-character", "$ git statu", "s", prompt),
        Interaction("erase-one-character", "$ git statusx", "\b \b", prompt),
        Interaction("echo-accented-character", "$ echo caf", "é", "$ echo café"),
        Interaction("recall-command-line", "$ gi", "\r\u001b[2K$ git status --short", "$ git status --short"),
        Interaction("progress-update", "Downloading 41%", "\rDownloading 42%", "Downloading 42%"),
        Interaction("short-command-output", "$ git status --short", "\r\n M src/Main.kt\r\n?? README.md\r\n$ ",
                    "$ git status --short\r\n M src/Main.kt\r\n?? README.md\r\n$ "),
        Interaction("file-picker-next-row", "> README.md\r\n  build.gradle", "\u001b[1;1H \u001b[2;1H>",
                    "  README.md\r\n> build.gradle", cursorColumn = 1),
      )
      for (case in cases) {
        println("INTERACTIVE START ${case.name}")
        val cursorOnly = case.before == case.after
        val visibility = if (cursorOnly) "\u001b[?25h" else "\u001b[?25l"
        suspend fun prepare(harness: PaintHarness, text: String) {
          harness.submit(CLEAR, sequence++)
          val padding = "\r\n".repeat(ROWS - 2 - text.count { it == '\n' })
          harness.submit("\u001b[2J\u001b[H" + visibility + text + padding, sequence++)
        }
        suspend fun position(harness: PaintHarness, text: String, column: Int) {
          val row = text.count { it == '\n' }
          harness.submitInteractive("\u001b[${row + 1};${column + 1}H", row, column)
        }
        val references = harnesses.mapValues { (_, harness) ->
          if (cursorOnly) null else {
            prepare(harness, case.after)
            position(harness, case.after, case.cursorColumn)
            harness.interactiveReference()
          }
        }
        val samples = backends.associateWith { LongArray(MEASURED_RUNS) }
        val bytes = backends.associateWith { LongArray(MEASURED_RUNS) }
        repeat(WARMUP_RUNS + MEASURED_RUNS) { run ->
          for (index in backends.indices) {
            val backend = backends[(run + index) % backends.size]
            val harness = harnesses.getValue(backend)
            if (run == 0) println("INTERACTIVE WARMUP ${backend.name} ${case.name}")
            val sample = withTimeout(15.seconds) {
              prepare(harness, case.before)
              position(harness, case.before, case.before.substringAfterLast('\n').length)
              awaitBenchmarkInputGap()
              harness.submitInteractive(case.payload, case.after.count { it == '\n' }, case.cursorColumn, references[backend])
            }
            if (run >= WARMUP_RUNS) {
              samples.getValue(backend)[run - WARMUP_RUNS] = sample.nanos
              bytes.getValue(backend)[run - WARMUP_RUNS] = sample.bytes
            }
          }
        }
        for ((backend, times) in samples) {
          val metric = if (cursorOnly) "output-to-cursor-ready-ms" else "output-to-verified-paint-ms"
          printSamples("interactive", backend, case.name, metric, times, 1_000_000.0)
          println("INTERACTIVE TOTAL ${backend.name} ${case.name} operations=$MEASURED_RUNS totalBytes=${bytes.getValue(backend).sum()}")
        }
      }
    }
    finally {
      withContext(NonCancellable) {
        try { for (harness in harnesses.values) harness.close() }
        finally { withContext(Dispatchers.EDT) { RepaintManager.setCurrentManager(previous) } }
      }
    }
  }

  private data class Interaction(
    val name: String,
    val before: String,
    val payload: String,
    val after: String,
    val cursorColumn: Int = after.substringAfterLast('\n').length,
  )

  @Test
  fun `compare fresh session rendering`(): Unit = timeoutRunBlocking(5.minutes) { compare(reuse = false) }

  @Test
  fun `compare reused session rendering`(): Unit = timeoutRunBlocking(5.minutes) { compare(reuse = true) }

  @Test
  fun `compare one MiB input to painted marker`(): Unit = timeoutRunBlocking(5.minutes) {
    val capacityKey = "new.terminal.output.capacity.kb"
    val linesKey = "terminal.buffer.max.lines.count"
    val previousCapacity = AdvancedSettings.getInt(capacityKey)
    val previousLines = AdvancedSettings.getInt(linesKey)
    val benchmark = TerminalRenderingBenchmark(projectFixture.get())
    val observer = PaintObserver()
    val previousObserver = withContext(Dispatchers.EDT) {
      RepaintManager.currentManager(null).also { RepaintManager.setCurrentManager(observer) }
    }
    val backends = configuredBackends
    val harnesses = LinkedHashMap<TerminalEmulatorType, PaintHarness>()
    val samples = backends.associateWith { LongArray(MEASURED_RUNS) }
    val stages = listOf("visible-ready", "render-history-ready", "document-history-ready", "oldest-scroll-paint")
    val stageSamples = backends.associateWith { backend ->
      val applicable = if (backend == TerminalEmulatorType.KetraTerm) stages - "document-history-ready" else stages
      applicable.associateWith { LongArray(MEASURED_RUNS) }
    }
    val payload = oneMiBLogPayload()
    assertThat(payload.toByteArray(Charsets.UTF_8)).hasSize(1_024_890)
    try {
      AdvancedSettings.setInt(capacityKey, 4096)
      AdvancedSettings.setInt(linesKey, 60_000)
      for (backend in backends) {
        harnesses[backend] = benchmark.createHarness(this, backend, observer)
      }
      println("PAINT CONFIG: 6000 lines; 1024890 bytes; 80x24; 5 warmups; 9 measurements; reused sessions")
      println("PAINT PRE-INPUT IDLE: ${benchmarkPreInputIdleMillis} ms; outside timing")
      println("PAINT ENDPOINT: input to software-painted marker; no compositor or display presentation")
      println("PAINT VALIDATION: exact complete history after timing, before the next sample")
      println("PAINT HISTORY: native KetraTerm views use retained rows; document views use the frontend Document")
      println("PAINT SCROLL: separate timer for one oldest viewport after full text verification; not all history painted")
      var sequence = 1
      repeat(WARMUP_RUNS + MEASURED_RUNS) { run ->
        for (index in backends.indices) {
          val backend = backends[(index + run) % backends.size]
          val harness = harnesses.getValue(backend)
          withTimeout(30.seconds) {
            harness.submit(CLEAR, sequence++)
            awaitBenchmarkInputGap()
            val latency = harness.submit(TerminalRenderingBenchmark.ERASE_MARKER + payload, sequence++, verifyHistory = true)
            val oldestPaint = harness.paintOldestHistory()
            if (run >= WARMUP_RUNS) {
              val sample = run - WARMUP_RUNS
              samples.getValue(backend)[sample] = latency
              val result = stageSamples.getValue(backend)
              result.getValue("visible-ready")[sample] = harness.visibleNanos
              result.getValue("render-history-ready")[sample] = harness.historyNanos
              result["document-history-ready"]?.set(sample, harness.documentHistoryNanos)
              result.getValue("oldest-scroll-paint")[sample] = oldestPaint
            }
            if (run == WARMUP_RUNS && System.getenv("TERMINAL_RENDER_IMAGE_DIR") != null) {
              withContext(Dispatchers.EDT) {
                harness.saveImage("reused", "one-mib-oldest")
              }
            }
            harness.returnToLive()
          }
        }
      }
      for ((backend, values) in samples) {
        val name = backend.name
        assertThat(values.all { it > 0 }).isTrue()
        println("PAINT SAMPLES $name: " + values.joinToString { "%.3f".format(it / 1_000_000.0) })
        for ((stage, times) in stageSamples.getValue(backend)) {
          println("PIPELINE SAMPLES $name $stage: " + times.joinToString { "%.3f".format(it / 1_000_000.0) })
        }
        values.sort()
        println("PAINT P50 $name: %.3f ms".format(values[MEASURED_RUNS / 2] / 1_000_000.0))
      }
    }
    finally {
      withContext(NonCancellable) {
        for (harness in harnesses.values) harness.close()
        withContext(Dispatchers.EDT) { RepaintManager.setCurrentManager(previousObserver) }
        AdvancedSettings.setInt(capacityKey, previousCapacity)
        AdvancedSettings.setInt(linesKey, previousLines)
      }
    }
  }
  @Test
  fun `compare repeated partial screen updates`(): Unit = timeoutRunBlocking(10.minutes) {
    val benchmark = TerminalRenderingBenchmark(projectFixture.get())
    val observer = PaintObserver()
    val previous = withContext(Dispatchers.EDT) {
      RepaintManager.currentManager(null).also { RepaintManager.setCurrentManager(observer) }
    }
    val harnesses = LinkedHashMap<TerminalEmulatorType, PaintHarness>()
    val workloads = listOf("status-line", "progress-bar", "local-panel", "dashboard")
    val updatesPerSample = 32
    val metrics = listOf("batch-ms", "mean-update-ms", "p95-update-ms", "paint-ms-per-update", "paint-bytes-per-update",
                         "allocated-bytes-per-update", "paint-calls-per-update", "painted-viewport-percent-per-update")
    val samples = LinkedHashMap<String, DoubleArray>()
    println("PARTIAL CONFIG: 80x24; 5 warmups; 9 measurements; 32 successive painted updates per sample; reused sessions")
    println("PARTIAL PAINT: use each renderer's requested regions; hidden cursor; no timed scroll")
    println("PARTIAL VALIDATION: unique marker for every update; compare the final image with a full paint after timing")
    println("PARTIAL ALLOCATION: recurring JVM allocation includes the adapter, platform, and benchmark control")
    println("PARTIAL PAINT ALLOCATION: paint calls on the EDT; excludes region collection")
    println("PARTIAL EXCLUDES: payload construction, startup, image verification, and display presentation")
    var sequence = 30_000
    try {
      for (backend in configuredBackends) {
        val harness = benchmark.createHarness(this, backend, observer)
        harnesses[backend] = harness
        withContext(Dispatchers.EDT) { harness.enableRequestedRepaints() }
        withTimeout(30.seconds) {
          harness.submit(CLEAR, sequence++)
          val background = buildString {
            append("\u001b[?25l")
            repeat(30) { append("terminal content 0123456789 ".repeat(2)).append("\r\n") }
          }
          harness.submit(background, sequence++)
        }
      }
      for (workload in workloads) {
        val rows = when (workload) {
          "status-line" -> listOf(22)
          "progress-bar" -> listOf(12)
          "local-panel" -> listOf(7, 8, 9, 10)
          else -> listOf(4, 12, 20)
        }
        val column = if (workload == "local-panel") 6 else 4
        val width = when (workload) {
          "progress-bar" -> 58
          "local-panel" -> 34
          else -> 48
        }
        repeat(WARMUP_RUNS + MEASURED_RUNS) { run ->
          val updates = List(updatesPerSample) { update ->
            val rgb = 0xFF000000.toInt() or (0x500000 + sequence++)
            val payload = buildString {
              append("\u001b[48;2;${(rgb ushr 16 and 255) xor 1};${rgb ushr 8 and 255};${rgb and 255}m")
              for ((widget, row) in rows.withIndex()) {
                val text = when (workload) {
                  "progress-bar" -> {
                    val filled = (update + 1) * 40 / updatesPerSample
                    "download [${"#".repeat(filled)}${"-".repeat(40 - filled)}] ${(update + 1) * 100 / updatesPerSample}%"
                  }
                  "status-line" -> "task $sequence processed=${update + 1} state=RUNNING"
                  "local-panel" -> "metric $widget value=${update + 1} frame=$sequence"
                  else -> "service $widget requests=${update + 1} frame=$sequence"
                }
                require(text.length < width)
                append("\u001b[$row;${column}H").append(text.padEnd(width))
              }
              append("\u001b[48;2;${rgb ushr 16 and 255};${rgb ushr 8 and 255};${rgb and 255}m")
              append("\u001b[${rows.last()};${column + width - 1}H ")
              append("\u001b[0m\u001b[24;1H")
            }
            PaintHarness.PartialUpdate(payload, rgb)
          }
          for (index in configuredBackends.indices) {
            val backend = configuredBackends[(index + run) % configuredBackends.size]
            val harness = harnesses.getValue(backend)
            awaitBenchmarkInputGap()
            println("PARTIAL RUN ${backend.name} $workload sample=$run")
            val result = withTimeout(60.seconds) { harness.submitPartialUpdates(updates) }
            assertThat(result.latencies.all { it > 0 }).isTrue()
            if (run >= WARMUP_RUNS) {
              val times = result.latencies.sorted()
              val values = doubleArrayOf(
                result.batchNanos / 1_000_000.0,
                result.latencies.average() / 1_000_000.0,
                times[(times.size * 95 + 99) / 100 - 1] / 1_000_000.0,
                result.paintNanos / 1_000_000.0 / updatesPerSample,
                result.paintBytes.toDouble() / updatesPerSample,
                result.allocatedBytes.toDouble() / updatesPerSample,
                result.paintCalls.toDouble() / updatesPerSample,
                result.paintedPixels * 100.0 / result.viewportPixels / updatesPerSample,
              )
              for (metric in metrics.indices) {
                val recorded = samples.getOrPut("${backend.name} $workload ${metrics[metric]}") { DoubleArray(MEASURED_RUNS) }
                recorded[run - WARMUP_RUNS] = values[metric]
              }
            }
          }
        }
      }
      for ((key, values) in samples) {
        assertThat(values.all { it >= 0 }).isTrue()
        println("PARTIAL SAMPLES $key: " + values.joinToString { "%.3f".format(it) })
        values.sort()
        println("PARTIAL P50 $key: %.3f".format(values[MEASURED_RUNS / 2]))
      }
    }
    finally {
      withContext(NonCancellable) {
        for (harness in harnesses.values) harness.close()
        withContext(Dispatchers.EDT) { RepaintManager.setCurrentManager(previous) }
      }
    }
  }

  @Test
  fun `compare extended terminal performance`(): Unit = timeoutRunBlocking(10.minutes) {
    val capacityKey = "new.terminal.output.capacity.kb"
    val linesKey = "terminal.buffer.max.lines.count"
    val oldCapacity = AdvancedSettings.getInt(capacityKey)
    val oldLines = AdvancedSettings.getInt(linesKey)
    val benchmark = TerminalRenderingBenchmark(projectFixture.get())
    val observer = PaintObserver()

    val backends = configuredBackends
    val harnesses = LinkedHashMap<TerminalEmulatorType, PaintHarness>()
    val scenarios = System.getProperty("ketraterm.benchmark.scenario")?.split(',')?.map {
      if (it == "cursor-motion") CURSOR_BURST_NAME else it
    }
    val availableKinds = listOf("huge-history", "mixed-sgr", "osc8-hyperlinks", "bidi", "complex-graphemes", "single-long-line", CURSOR_BURST_NAME)
    require(scenarios == null || scenarios.all { it in availableKinds }) { "Unknown performance scenarios: $scenarios" }
    val kinds = availableKinds.filter { scenarios == null || it in scenarios }
    require(kinds.isNotEmpty()) { "Unknown performance scenarios: $scenarios" }
    println("EXTENDED PRE-INPUT IDLE: $benchmarkPreInputIdleMillis ms; outside timing; workload condition, not a completion barrier")
    println("EXTENDED VALIDATION: retained text and marker pixels; does not certify SGR, link, bidi, or grapheme visual fidelity")
    println("EXTENDED INTERACTION: selection state / search result viewport followed by software paint; highlight pixels not verified")
    val previousObserver = withContext(Dispatchers.EDT) {
      RepaintManager.currentManager(null).also { RepaintManager.setCurrentManager(observer) }
    }
    var sequence = 1000
    try {
      AdvancedSettings.setInt(capacityKey, 10240)
      AdvancedSettings.setInt(linesKey, 180_000)
      for (backend in backends) harnesses[backend] = benchmark.createHarness(this, backend, observer)
      for (kind in kinds) {
        val plain = StringBuilder()
        val payload = StringBuilder()
        val directWorkload = if (kind == "single-long-line" || kind == CURSOR_BURST_NAME) {
          TerminalRenderingBenchmark.workloads().single { it.name == kind }
        }
        else null
        val lines = when {
          directWorkload != null -> 0
          kind == "huge-history" -> 26_000
          else -> 6_000
        }
        repeat(lines) { row ->
          val prefix = "2026-08-07 12:34:56,%03d [%6d] INFO - request %d state=RUNNING ".format(row % 1000, row, row)
          val body = when (kind) {
            "bidi" -> "English مرحبا بالعالم 123 שלום עולם 456 العربية Latin ".repeat(2)
            "complex-graphemes" -> "e\u0301 a\u0308 👩🏽‍💻 👨‍👩‍👧‍👦 🇦🇲 1⃣ क्‍ष ".repeat(3)
            else -> "abcdefghijklmnopqrstuvwxyz-0123456789 ".repeat(3)
          }
          plain.append(prefix).append(body).append("\r\n")
          payload.append(prefix)
          when (kind) {
            "mixed-sgr" -> payload.append("\u001b[1;38;2;200;40;80;48;5;17m").append(body.substring(0, 37))
              .append("\u001b[0;4;38;5;42m").append(body.substring(37, 74))
              .append("\u001b[0;3;9;7m").append(body.substring(74)).append("\u001b[0m")
            "osc8-hyperlinks" -> payload.append("\u001b]8;id=$row;https://example.com/log/$row\u001b\\")
              .append(body).append("\u001b]8;;\u001b\\")
            else -> payload.append(body)
          }
          payload.append("\r\n")
        }
        if (directWorkload != null) {
          payload.append(directWorkload.payload)
          plain.append(directWorkload.payload)
        }
        val verifyHistory = kind != CURSOR_BURST_NAME
        println("EXTENDED CHECK $kind: " + if (verifyHistory) "retained text and marker pixels" else "marker pixels only; cursor state not validated")
        val input = TerminalRenderingBenchmark.ERASE_MARKER + payload
        val expected = TerminalRenderingBenchmark.textAfterErasingMarker(plain.toString())
        println("EXTENDED CONFIG $kind lines=$lines bytes=${payload.toString().toByteArray(Charsets.UTF_8).size} grid=80x24")
        val metrics = if (kind == "huge-history") listOf("selection-state-then-paint", "search-result-viewport-then-paint")
        else listOf("input-painted")
        val samples = backends.associateWith { metrics.associateWith { LongArray(MEASURED_RUNS) } }
        repeat(WARMUP_RUNS + MEASURED_RUNS) { run ->
          for (offset in backends.indices) {
            val backend = backends[(run + offset) % backends.size]
            val harness = harnesses.getValue(backend)
            println("EXTENDED RUN $kind ${backend.name} sample=$run")
            withTimeout(45.seconds) {
              harness.submit(CLEAR, sequence++)
              awaitBenchmarkInputGap()
              val painted = harness.submit(
                input, sequence++, verifyHistory = verifyHistory, expectedTextBeforeMarker = expected, canonicalHistory = kind == "complex-graphemes",
              )
              val measured = if (kind == "huge-history") {
                // Start each interaction only after exact history verification and a completed scroll to the oldest rows.
                harness.paintOldestHistory()
                val selection = harness.selectHistory()
                val search = harness.searchHistory("request 25999 ")
                listOf(selection, search)
              }
              else listOf(painted)
              if (run >= WARMUP_RUNS) {
                for ((index, metric) in metrics.withIndex()) samples.getValue(backend).getValue(metric)[run - WARMUP_RUNS] = measured[index]
              }
              harness.returnToLive()
            }
          }
        }
        for ((backend, results) in samples) {
          val name = backend.name
          for ((metric, values) in results) {
            assertThat(values.all { it > 0 }).isTrue()
            println("EXTENDED SAMPLES $name $kind $metric: " + values.joinToString { "%.3f".format(it / 1_000_000.0) })
          }
        }
      }
    }
    finally {
      withContext(NonCancellable) {
        for (harness in harnesses.values) harness.close()
        withContext(Dispatchers.EDT) { RepaintManager.setCurrentManager(previousObserver) }
        AdvancedSettings.setInt(capacityKey, oldCapacity)
        AdvancedSettings.setInt(linesKey, oldLines)
      }
    }
  }

  private suspend fun CoroutineScope.compare(reuse: Boolean) {
    val benchmark = TerminalRenderingBenchmark(projectFixture.get())
    val backends = configuredBackends
    val mode = if (reuse) "reused" else "fresh"
    val observer = PaintObserver()
    val previous = withContext(Dispatchers.EDT) {
      RepaintManager.currentManager(null).also { RepaintManager.setCurrentManager(observer) }
    }
    val harnesses = LinkedHashMap<TerminalEmulatorType, PaintHarness>()
    var sequence = 1
    var sessionCount = 0
    try {
      if (reuse) for (backend in backends) {
        harnesses[backend] = benchmark.createHarness(this, backend, observer)
        sessionCount++
      }
      println("RENDER PRE-INPUT IDLE: $benchmarkPreInputIdleMillis ms; outside timing")
      println("RENDER VALIDATION: marker only; no complete-history or visual-feature correctness claim")
      println("RENDER CONFIG $mode: ${COLUMNS}x$ROWS; $WARMUP_RUNS warmup runs; $MEASURED_RUNS measured runs")
      println("RENDER SCOPE: process output to software paint; no shell execution, window composition, or display presentation")
      println("RENDER PAINT: full viewport; $PAINTS_PER_SAMPLE paints per sample; " +
              "EDT-only repeated-paint allocation; may include cache refresh; excludes background and native allocation")
      for (workload in TerminalRenderingBenchmark.workloads()) {
        val samples = backends.associateWith { RenderSamples() }
        println("RENDER START $mode ${workload.name}: ${workload.payload.toByteArray(Charsets.UTF_8).size} bytes")
        repeat(WARMUP_RUNS + MEASURED_RUNS) { run ->
          for (index in backends.indices) {
            val backend = backends[(index + run) % backends.size]
            val harness = if (reuse) harnesses.getValue(backend)
            else benchmark.createHarness(this, backend, observer).also { sessionCount++ }
            try {
              withTimeout(30.seconds) {
                harness.submit(CLEAR, sequence++)
                if (workload.prelude.isNotEmpty()) harness.submit(workload.prelude, sequence++)
                awaitBenchmarkInputGap()
                val latency = harness.submit(workload.payload, sequence++)
                withContext(Dispatchers.EDT) {
                  harness.measurePaint()
                  if (run >= WARMUP_RUNS) {
                    val result = samples.getValue(backend)
                    val sample = run - WARMUP_RUNS
                    result.latency[sample] = latency
                    result.paint[sample] = harness.paintNanos
                    result.allocations[sample] = harness.paintBytes
                  }
                  if (run == WARMUP_RUNS && System.getenv("TERMINAL_RENDER_IMAGE_DIR") != null) {
                    harness.saveImage(mode, workload.name)
                  }
                }
              }
            }
            finally {
              if (!reuse) withContext(NonCancellable) { harness.close() }
            }
          }
        }
        for (backend in backends) {
          val result = samples.getValue(backend)
          assertThat(result.latency.all { it > 0 }).isTrue()
          assertThat(result.paint.all { it > 0 }).isTrue()
          assertThat(result.allocations.all { it >= 0 }).isTrue()
          printSamples(mode, backend, workload.name, "input-to-paint-ms", result.latency, 1_000_000.0)
          printSamples(mode, backend, workload.name, "paint-ms", result.paint, 1_000_000.0)
          printSamples(mode, backend, workload.name, "paint-bytes", result.allocations, 1.0)
        }
      }
      println("RENDER SESSION COUNT $mode: $sessionCount")
    }
    finally {
      withContext(NonCancellable) {
        for (harness in harnesses.values) harness.close()
        withContext(Dispatchers.EDT) { RepaintManager.setCurrentManager(previous) }
      }
    }
  }

  private class RenderSamples {
    val latency = LongArray(MEASURED_RUNS)
    val paint = LongArray(MEASURED_RUNS)
    val allocations = LongArray(MEASURED_RUNS)
  }

  private fun printSamples(
    mode: String,
    backend: TerminalEmulatorType,
    workload: String,
    metric: String,
    values: LongArray,
    unit: Double,
  ) {
    println("RENDER SAMPLES $mode ${backend.name} $workload $metric: " + values.joinToString { "%.3f".format(it / unit) })
    values.sort()
    println("RENDER P50 $mode ${backend.name} $workload $metric: %.3f".format(values[MEASURED_RUNS / 2] / unit))
  }

  companion object {
    private val projectFixture = projectFixture()
    private const val COLUMNS = TerminalRenderingBenchmark.COLUMNS
    private const val ROWS = TerminalRenderingBenchmark.ROWS
    private const val WARMUP_RUNS = 5
    private const val MEASURED_RUNS = 9
    private const val PAINTS_PER_SAMPLE = TerminalRenderingBenchmark.PAINTS_PER_SAMPLE
    private const val CLEAR = TerminalRenderingBenchmark.CLEAR
  }
}

internal fun oneMiBLogPayload(): String = buildString {
  repeat(6_000) { index ->
    append("2026-08-07 12:34:56,").append("%03d".format(index % 1000))
    append(" [").append("%6d".format(index)).append("] INFO - #c.i.o.diagnostic.ExampleComponent - ")
    append("request ").append(index % 10_000).append(" handled in 12 ms, state=RUNNING, payload: ")
    append("abcdefghijklmnopqrstuvwxyz-0123456789\r\n")
  }
}
