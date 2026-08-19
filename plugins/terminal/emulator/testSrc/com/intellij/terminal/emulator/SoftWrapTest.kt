// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [TerminalRow.wrapped]: which rows report that they soft-wrapped into the next one.
 *
 * The flag is the only way a consumer can tell a soft wrap from a hard line break, and it is what lets one logical
 * line be rebuilt from the grid rows it occupies — `GhosttyTerminalSession` joins rows without a `'\n'` while it is
 * set, and derives the cursor's logical line and column the same way. It comes from the engine
 * (`ghostty_grid_ref_row` + `ghostty_row_get(GHOSTTY_ROW_DATA_WRAP)`), which the binding has to read per row; every
 * `isTrue()` assertion below fails if that read regresses to a constant `false`.
 *
 * Wrapping of the *text* is covered by [TextBufferTest]; reflow of wrapped lines across a resize by [ResizeTest].
 */
class SoftWrapTest {

  /**
   * Filling the last column only arms the deferred wrap ("phantom") state — the row has not wrapped until a glyph
   * actually lands on the next one, so the flag must stay false until then. Confusing the two would make a full
   * row swallow the following line break.
   */
  @Test
  fun pendingWrapIsNotYetAWrappedRow() = session(5, 4) { session ->
    session.write("abcde") // exactly fills row 0
    session.assertCursorPosition(5, 1) // still on row 0, wrap pending
    assertThat(session.screenLine(0).wrapped).describedAs("full row, nothing beyond it yet").isFalse()

    session.write("f") // now the wrap really happens
    session.assertCursorPosition(2, 2)
    assertThat(session.screenLine(0).wrapped).describedAs("row 0 wrapped into row 1").isTrue()
  }

  /** Only the rows that ran out of columns are wrapped — the last piece of the line is not. */
  @Test
  fun everyRowOfALongLineButTheLastIsWrapped() = session(5, 4) { session ->
    session.write("abcdefghijkl") // 12 chars over three rows: "abcde" / "fghij" / "kl"

    session.assertScreenLines("abcde", "fghij", "kl")
    assertThat(session.screenLine(0).wrapped).describedAs("row 0").isTrue()
    assertThat(session.screenLine(1).wrapped).describedAs("row 1").isTrue()
    assertThat(session.screenLine(2).wrapped).describedAs("row 2 is the last piece").isFalse()
    assertThat(session.screenLine(3).wrapped).describedAs("row 3 is empty").isFalse()
  }

  @Test
  fun hardLineBreakDoesNotMarkTheRowWrapped() = session(5, 4) { session ->
    session.write("ab\r\ncd\r\nef")

    assertThat((0 until 4).map { session.screenLine(it).wrapped })
      .describedAs("CRLF-separated rows")
      .containsExactly(false, false, false, false)
  }

  /**
   * With autowrap off (DECAWM, `CSI ?7l`) printing past the last column keeps overwriting it instead of moving to
   * the next row, so nothing wraps.
   */
  @Test
  fun autowrapOffNeverMarksARowWrapped() = session(5, 4) { session ->
    session.write(csi("?7l") + "abcdefgh")

    session.assertScreenLines("abcdh") // 'e'..'h' all landed in the last column
    assertThat(session.screenLine(0).wrapped).isFalse()
    assertThat(session.screenLine(1).wrapped).isFalse()
  }

  /** A wide glyph that no longer fits moves to the next row as a whole, which wraps the row it left behind. */
  @Test
  fun wideGlyphThatDoesNotFitWrapsTheRow() = session(5, 4) { session ->
    session.write("abcd") // leaves one free column
    session.write("生")   // needs two, so it moves to the next row as a whole

    session.assertScreenRow(0, "abcd") // the last cell stays blank
    session.assertScreenRow(1, "生")
    assertThat(session.screenLine(0).wrapped).describedAs("row 0 wrapped for the wide glyph").isTrue()
    assertThat(session.screenLine(1).wrapped).isFalse()
  }

  /**
   * The flag has to survive the row scrolling off into scrollback: a consumer appending finalized history to a
   * document reads those rows, not the active screen, so losing it there would split every scrolled-off long line.
   */
  @Test
  fun scrollbackKeepsTheWrappedFlag() = session(5, 3) { session ->
    session.write("abcdefghijkl\r\nxx\r\nyy\r\nzz") // the wrapped line scrolls off a 3-row screen

    session.expectFullRebuild()
    session.assertScrollbackLines("abcde", "fghij", "kl")
    session.assertScreenLines("xx", "yy", "zz")

    assertThat(session.emulator.scrollbackLine(0).wrapped).describedAs("scrollback row 0").isTrue()
    assertThat(session.emulator.scrollbackLine(1).wrapped).describedAs("scrollback row 1").isTrue()
    assertThat(session.emulator.scrollbackLine(2).wrapped).describedAs("scrollback row 2 ends the line").isFalse()
    assertThat((0 until 3).map { session.screenLine(it).wrapped })
      .describedAs("the short screen rows")
      .containsExactly(false, false, false)
  }

  @Test
  fun eraseClearsTheWrappedFlag() = session(5, 3) { session ->
    session.write("abcdefg")
    assertThat(session.screenLine(0).wrapped).isTrue()

    session.write(csi("2J")) // ED 2: the rows are blank, so nothing wraps any more

    session.expectFullRebuild()
    session.assertScreenLines()
    assertThat(session.screenLine(0).wrapped).isFalse()
  }

  @Test
  fun resetClearsTheWrappedFlag() = session(5, 3) { session ->
    session.write("abcdefg")
    assertThat(session.screenLine(0).wrapped).isTrue()

    session.write(esc("c")) // RIS

    session.expectFullRebuild()
    session.assertScreenLines()
    assertThat(session.screenLine(0).wrapped).isFalse()
  }

  /** The alternate screen wraps like the primary one (a full-screen TUI relies on the same geometry). */
  @Test
  fun alternateScreenRowsReportWrapping() = session(5, 3) { session ->
    session.useAlternateBuffer(true)
    session.write("abcdefg")

    session.expectFullRebuild()
    session.assertScreenLines("abcde", "fg")
    assertThat(session.screenLine(0).wrapped).describedAs("row 0 of the alternate screen").isTrue()
    assertThat(session.screenLine(1).wrapped).isFalse()
  }

  /**
   * The flag belongs to the row, not to the glyphs written last: overwriting cells inside a wrapped row leaves it
   * wrapped.
   *
   * ENGINE-SPECIFIC: erasing the *continuation* row does not clear it either, so a row can report that it wraps
   * into a blank row. That is harmless for a consumer joining rows (it appends nothing), and it matches how the
   * flag is stored — as a property of the row that ran out of columns, re-evaluated only when that row is rewritten.
   */
  @Test
  fun overwritingInsideAWrappedRowKeepsItWrapped() = session(5, 3) { session ->
    session.write("abcdefg")

    session.write(csi("1;1H") + "XY")
    session.assertScreenRow(0, "XYcde")
    assertThat(session.screenLine(0).wrapped).describedAs("after overwriting inside row 0").isTrue()

    session.write(csi("2;1H") + csi("2K")) // erase the continuation row
    session.assertScreenRow(1, "")
    assertThat(session.screenLine(0).wrapped).describedAs("continuation erased, row 0 unchanged").isTrue()
  }

  /** Out-of-range rows read as empty rows (see [TerminalEmulator.screenLine]), which are never wrapped. */
  @Test
  fun outOfRangeRowIsNotWrapped() = session(5, 3) { session ->
    session.write("abcdefg")

    assertThat(session.screenLine(99).wrapped).isFalse()
    assertThat(session.emulator.scrollbackLine(99).wrapped).isFalse()
  }
}
