// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.assertj.core.api.Assertions.assertThat
import java.nio.charset.StandardCharsets

/**
 * Runs [block] against a fresh [EmulatorTestSession] of the given size, closing it afterward. Lets a
 * test read as a single expression:
 *
 * ```
 * @Test
 * fun example() = session(15, 10) { session ->
 *   session.write("hello")
 *   session.assertScreenLines("hello")
 * }
 * ```
 */
internal fun session(width: Int, height: Int, maxScrollbackBytes: Int = 1024 * 1024, block: (EmulatorTestSession) -> Unit) {
  EmulatorTestSession(width, height, maxScrollbackBytes).use(block)
}

/**
 * A test harness around a [TerminalEmulator]: it drives the emulator with VT byte sequences and reads
 * the resulting screen + scrollback back through the public emulator API only. The high-level methods
 * mirror the VT operations to keep tests readable.
 */
internal class EmulatorTestSession(width: Int, height: Int, maxScrollbackBytes: Int) : AutoCloseable {

  val emulator: TerminalEmulator = createTerminalEmulator(TerminalSize(width, height), maxScrollbackBytes)

  // Records everything the terminal writes back to the host (query replies such as DSR / DA, OSC
  // reports, etc.), decoded as UTF-8, so tests can assert them via assertResponses().
  private val responses = ArrayList<String>()

  // Counts BEL (0x07) rings delivered via [TerminalListener.onBell]; asserted via assertBellCount().
  private var bellCount = 0

  // A second view of the screen + scrollback rebuilt ONLY from the emulator's incremental API, validated
  // against the naive full read in every assert so each assertion also checks the change-tracking contract.
  private val incrementalBuffer: IncrementalTextBuffer = IncrementalTextBuffer(emulator)

  init {
    emulator.listener = object : TerminalListener {
      override fun onRespondToHost(data: ByteArray) {
        responses.add(String(data, StandardCharsets.UTF_8))
      }

      override fun onBell() {
        bellCount++
      }
    }

    // Consume the render state's initial full "paint": its first update reports FULL because the cached
    // viewport size goes from 0x0 to WxH (an initial resize), which would otherwise mask the first real
    // edit. Priming here starts the test from a clean, non-dirty state so the first change a test makes
    // is reported at its true granularity (Rows for a localized edit).
    incrementalBuffer.expectFullRebuild()
    incrementalBuffer.sync()
  }

  // ---- input: text ----

  fun write(s: String): EmulatorTestSession = apply { emulator.write(s) }

  fun writeLinesWithCrlf(
    lines: List<String>,
    addCrlfAfterLast: Boolean = false,
  ): EmulatorTestSession = apply {
    for ((index, line) in lines.withIndex()) {
      emulator.write(line)
      if (index < lines.size - 1 || addCrlfAfterLast) {
        crlf()
      }
    }
  }

  fun crlf(): EmulatorTestSession = apply { emulator.write("\r\n") } // CR + LF

  // ---- input: control sequences ----

  /** CUP: 1-based column [x], row [y]; values &le; 0 clamp to 1 (home). */
  fun cursorPosition(x: Int, y: Int) = emulator.write(csi("${maxOf(1, y)};${maxOf(1, x)}H"))

  /** DECSTBM: 1-based inclusive top/bottom. */
  fun setScrollingRegion(top: Int, bottom: Int) = emulator.write(csi("$top;${bottom}r"))

  fun insertLines(count: Int) = emulator.write(csi("${count}L"))         // IL
  fun deleteLines(count: Int) = emulator.write(csi("${count}M"))         // DL
  fun deleteCharacters(count: Int) = emulator.write(csi("${count}P"))    // DCH
  fun eraseCharacters(count: Int) = emulator.write(csi("${count}X"))     // ECH
  fun insertBlankCharacters(count: Int) = emulator.write(csi("$count@")) // ICH

  /** DECSC. */
  fun saveCursor() = emulator.write(esc("7"))

  /** DECRC. */
  fun restoreCursor() = emulator.write(esc("8"))

  /** Erase entire display (ED 2); does not move the cursor or touch scrollback. */
  fun clearScreen() = emulator.write(csi("2J"))

  fun useAlternateBuffer(enabled: Boolean) = emulator.write(csi("?1049${if (enabled) "h" else "l"}"))

  fun resize(columns: Int, rows: Int) {
    incrementalBuffer.onResize()
    // A resize always reflows into a full rebuild, so approve it here — resize tests need no explicit
    // expectFullRebuild() before the following assertion.
    incrementalBuffer.expectFullRebuild()
    emulator.resize(TerminalSize(columns, rows))
  }

  /**
   * Approves the next screen/scrollback assertion to perform a full incremental-mirror rebuild (see
   * [IncrementalTextBuffer.expectFullRebuild]). Call before an assertion whose preceding change legitimately
   * repaints the whole screen (resize, clear/reset, alternate-screen switch, first paint); otherwise a full
   * rebuild fails, flagging an unexpected whole-screen repaint.
   */
  fun expectFullRebuild(): EmulatorTestSession = apply { incrementalBuffer.expectFullRebuild() }

  // ---- reading back (public emulator API only) ----

  /**
   * Active rows up to the last non-empty one; trailing empty rows at the bottom of the screen are
   * dropped (interior/leading empty rows are kept as ""). Per row, trailing empty cells are dropped
   * and interior empty cells rendered as spaces, so column positions (erase/insert/tab) are preserved.
   */
  private fun screenLines(): List<String> {
    val texts = (0 until emulator.size.rows).map { emulator.screenLine(it).toStyledText().text }
    return texts.subList(0, texts.indexOfLast { it.isNotEmpty() } + 1)
  }

  /**
   * Asserts the active screen lines ([screenLines], trailing empty rows dropped) equal [expectedLines],
   * validating both the naive full read and the incrementally-maintained mirror.
   */
  fun assertScreenLines(vararg expectedLines: String) = assertScreenLines(expectedLines.toList())

  fun assertScreenLines(expectedLines: List<String>) {
    incrementalBuffer.sync()
    assertThat(screenLines()).isEqualTo(expectedLines)
    assertThat(incrementalBuffer.screenLines())
      .describedAs("incremental screen mismatch")
      .isEqualTo(expectedLines)
  }

  /**
   * Asserts that active-screen row [row] (0-based) reads exactly [expectedText], validating both the naive
   * full read and the incrementally-maintained mirror.
   *
   * Unlike [assertScreenLines] this addresses one row at a time, so trailing empty rows stay assertable —
   * what a whole-screen check (see [VttestTest]) needs to state that the rest of the grid is blank.
   */
  fun assertScreenRow(row: Int, expectedText: String) {
    require(row in 0 until emulator.size.rows) {
      "row $row is outside the screen (0..${emulator.size.rows - 1}); out-of-range rows read as empty and would assert vacuously"
    }
    incrementalBuffer.sync()
    assertThat(emulator.screenLine(row).toStyledText().text)
      .describedAs("screen row $row")
      .isEqualTo(expectedText)
    assertThat(incrementalBuffer.screenRow(row))
      .describedAs("incremental screen row $row mismatch")
      .isEqualTo(expectedText)
  }

  /** Scrollback-row texts, oldest first (trailing empty cells dropped per row). */
  private fun scrollbackLineTexts(): List<String> =
    (0 until emulator.scrollbackRows).map { emulator.scrollbackLine(it).toStyledText().text }

  /**
   * Asserts the scrollback lines ([scrollbackLineTexts], oldest first) equal [expectedLines], validating
   * both the naive full read and the incrementally-maintained mirror.
   */
  fun assertScrollbackLines(vararg expectedLines: String) = assertScrollbackLines(expectedLines.toList())

  fun assertScrollbackLines(expectedLines: List<String>) {
    incrementalBuffer.sync()
    assertThat(emulator.scrollbackRows).isEqualTo(expectedLines.size)
    assertThat(scrollbackLineTexts()).isEqualTo(expectedLines)
    assertThat(incrementalBuffer.scrollbackLines())
      .describedAs("incremental scrollback mismatch")
      .isEqualTo(expectedLines)
  }

  /** Number of rows currently retained in scrollback. */
  fun scrollbackRowCount(): Int = emulator.scrollbackRows

  /**
   * The full accumulated history from the incremental mirror: every line ever finalized into scrollback, oldest
   * first, including lines the emulator has since evicted from its bounded scrollback. See
   * [IncrementalTextBuffer.fullScrollbackLines].
   */
  fun fullScrollbackLines(): List<String> = incrementalBuffer.fullScrollbackLines()

  /** The full text buffer: [fullScrollbackLines] followed by the active screen. See [IncrementalTextBuffer.fullBufferLines]. */
  fun fullBufferLines(): List<String> = incrementalBuffer.fullBufferLines()

  /** The active-screen row [y] (0-based) for cell-level assertions (e.g. double-width). */
  fun screenLine(y: Int): TerminalRow = emulator.screenLine(y)

  // ---- lower-level emulator state (cursor, input modes, change tracking, responses) ----

  val cursor: Cursor get() = emulator.cursor
  val cursorShape: CursorShape get() = emulator.cursorShape
  val cursorBlinking: Boolean get() = emulator.cursorBlinking
  fun paletteColor(index: Int): TerminalColor.Rgb = emulator.paletteColor(index)
  val title: String get() = emulator.title
  val progress: TerminalProgress? get() = emulator.progress
  val foregroundColor: TerminalColor.Rgb? get() = emulator.foregroundColor
  val backgroundColor: TerminalColor.Rgb? get() = emulator.backgroundColor
  val usingAlternateScreen: Boolean get() = emulator.usingAlternateScreen
  val bracketedPaste: Boolean get() = emulator.bracketedPaste
  val synchronizedOutput: Boolean get() = emulator.synchronizedOutput
  val mouseProtocol: MouseProtocol get() = emulator.mouseProtocol
  val mouseEncoding: MouseEncoding get() = emulator.mouseEncoding

  /**
   * Asserts that the host responses recorded so far (query replies such as DSR / DA, OSC reports),
   * each decoded as UTF-8, equal [expectedResponses], in order.
   */
  fun assertResponses(vararg expectedResponses: String) {
    assertThat(responses).isEqualTo(expectedResponses.toList())
  }

  /** Asserts that [expected] bells (BEL, 0x07) have been rung so far. */
  fun assertBellCount(expected: Int) {
    assertThat(bellCount).isEqualTo(expected)
  }

  var customCommandListener: TerminalCustomCommandListener?
    get() = emulator.customCommandListener
    set(value) {
      emulator.customCommandListener = value
    }

  /** Consumes and returns the screen damage since the previous call. */
  fun takeChanges(): ScreenChange = emulator.takeChanges()

  fun assertCursorPosition(expectedOneBasedX: Int, expectedOneBasedY: Int) {
    val cursor = emulator.cursor
    assertThat("cursorX=" + (cursor.column + 1) + ", cursorY=" + (cursor.row + 1))
      .isEqualTo("cursorX=$expectedOneBasedX, cursorY=$expectedOneBasedY")
  }

  /** Asserts the cursor's current drawing [shape] and [blinking] state together. */
  fun assertCursorStyle(shape: CursorShape, blinking: Boolean) {
    assertThat("shape=$cursorShape, blinking=$cursorBlinking")
      .isEqualTo("shape=$shape, blinking=$blinking")
  }

  override fun close() {
    incrementalBuffer.close()
    emulator.close()
  }
}

internal val ESC_CHAR: Char = Char(27)
internal val ESC_STR: String = ESC_CHAR.toString()
internal val BELL_CHAR: Char = Char(7)

// ---- VT sequence builders ----

/** Terminator that closes an OSC (Operating System Command) sequence. */
internal enum class OscTerminator(val sequence: String) {
  /** BEL (0x07). */
  BELL(BELL_CHAR.toString()),

  /** ST — String Terminator: ESC + backslash (0x1B 0x5C). */
  ST(ESC_STR + "\\"),
}

/** Prefixes [body] with `ESC` */
internal fun esc(body: String): String = "$ESC_STR$body"

/** Wraps [body] in a CSI (Control Sequence Introducer, `ESC [`) sequence. */
internal fun csi(body: String): String = "$ESC_STR[$body"

/** Wraps [body] in an OSC (Operating System Command, `ESC ]`) sequence closed by [terminator]. */
internal fun osc(body: String, terminator: OscTerminator = OscTerminator.BELL): String {
  return "$ESC_STR]$body${terminator.sequence}"
}

// ---- grid rendering helpers (used by the session above and by cell-level tests) ----

/** The [CellWidth] of every cell up to (and including) the last non-blank cell of the row. */
internal fun TerminalRow.contentWidths(): List<CellWidth> {
  val lastContent = cells.indexOfLast { it.codePoint != 0 || it.width == CellWidth.SPACER }
  return cells.subList(0, lastContent + 1).map { it.width }
}
