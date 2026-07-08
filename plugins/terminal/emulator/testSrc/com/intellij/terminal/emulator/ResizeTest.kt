// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Resize and reflow of the main and alternate screen buffers, plus buffer switching across a resize,
 * driven through the [TerminalEmulator] API. Each test feeds VT bytes (including a resize) and reads
 * the resulting screen + scrollback back.
 *
 * Unlike the plain VT operations in [TextBufferTest], resize-time reflow is not a standardized VT
 * operation: the engine has its own reflow and row-anchoring algorithm, so the asserted wrapping,
 * scrollback split and cursor position describe *this engine's* behavior. Comments marked
 * "ENGINE-SPECIFIC" flag the spots where that is a deliberate engine choice rather than a universal
 * rule.
 */
class ResizeTest {

  @Test
  fun resizeReflowsAndKeepsRendering() = session(20, 5) { session ->
    session.write("abcdefghij")   // 10 chars on row 0
    session.resize(5, 5)                // width 5 -> soft-wrap

    // "abcdefghij" reflowed at width 5 -> "abcde" / "fghij".
    session.assertScreenLines("abcde", "fghij")
  }

  // ===================== main buffer: height-only resize =====================

  @Test
  fun mainBufferResizeToBiggerHeight() = session(5, 5) { session ->
    session
      .write("line").crlf()
      .write("line2").crlf()
      .write("line3").crlf()
      .write("li")
    session.assertCursorPosition(3, 4)

    session.resize(10, 10)

    assertThat(session.scrollbackRowCount()).isZero()
    session.assertScreenLines("line", "line2", "line3", "li")
    session.assertCursorPosition(3, 4)
  }

  @Test
  fun mainBufferResizeToSmallerHeight() = session(5, 5) { session ->
    session
      .write("line").crlf()
      .write("line2").crlf()
      .write("line3").crlf()
      .write("li")
    session.assertCursorPosition(3, 4)

    session.resize(10, 2)

    session.assertScrollbackLines("line", "line2")
    session.assertScreenLines("line3", "li")
    session.assertCursorPosition(3, 2)
  }

  @Test
  fun mainBufferResizeToSmallerHeightAndBack() = session(5, 5) { session ->
    session
      .write("line").crlf()
      .write("line2").crlf()
      .write("line3").crlf()
      .write("line4").crlf()
      .write("li")
    session.assertCursorPosition(3, 5)

    session.resize(10, 2)

    session.assertScrollbackLines("line", "line2", "line3")
    session.assertScreenLines("line4", "li")
    session.assertCursorPosition(3, 2)

    session.resize(5, 5)

    // ENGINE-SPECIFIC: when the screen grows again, the rows previously moved to scrollback stay
    // there — the screen grows by adding blank rows at the bottom rather than pulling scrollback
    // back in.
    assertThat(session.scrollbackRowCount()).isEqualTo(3)
    session.assertScrollbackLines("line", "line2", "line3")
    session.assertScreenLines("line4", "li")
    session.assertCursorPosition(3, 2)
  }

  @Test
  fun mainBufferResizeToSmallerHeightAndKeepCursorVisible() = session(10, 4) { session ->
    session.write("line1")
    session.crlf()
    session.write("line2")
    session.crlf()
    session.write("line3")
    session.crlf()

    session.assertCursorPosition(1, 4)

    session.resize(10, 3)
    session.assertScrollbackLines("line1")
    session.assertScreenLines("line2", "line3")
    session.assertCursorPosition(1, 3)
  }

  @Test
  fun mainBufferResizeInHeightWithScrolling() = session(5, 2) { session ->
    // Seed scrollback the way a real pty would: let the first lines scroll off a 2-row screen before
    // growing it.
    session
      .write("line").crlf()
      .write("line2").crlf()
      .write("line3").crlf()
      .write("li")
    session.assertCursorPosition(3, 2)

    session.resize(10, 5)

    assertThat(session.scrollbackRowCount()).isZero()
    session.assertScreenLines("line", "line2", "line3", "li")
    session.assertCursorPosition(3, 4)
  }

  @Test
  fun mainBufferClearAndResizeVertically() = session(10, 4) { session ->
    session
      .write("hi>").crlf()
      .write("hi2>")

    session.clearScreen()

    session.cursorPosition(0, 0)
    session.write("hi3>")

    session.assertCursorPosition(5, 1)

    session.resize(10, 3)

    session.assertScrollbackLines()
    session.assertScreenLines("hi3>")
    session.assertCursorPosition(5, 1)
  }

  @Test
  fun mainBufferInitialResize() = session(10, 24) { session ->
    session.write("hi>")

    session.assertCursorPosition(4, 1)

    session.resize(10, 3)

    session.assertScrollbackLines()
    session.assertScreenLines("hi>")
    session.assertCursorPosition(4, 1)
  }

  // ===================== main buffer: width / both reflow =====================

  @Test
  fun mainBufferResizeWidthScenario1() = session(15, 24) { session ->
    session.write("$ cat long.txt")
    session.crlf()
    session.write("1_2_3_4_5_6_7_8")
    session.write("_9_10_11_12_13_")
    session.write("14_15_16_17_18_")
    session.write("19_20_21_22_23_")
    session.write("24_25_26")
    session.crlf()
    session.write("$ ")
    session.assertCursorPosition(3, 7)
    session.assertScrollbackLines()

    session.resize(20, 7)

    session.assertScrollbackLines()
    session.assertScreenLines(
      "$ cat long.txt",
      "1_2_3_4_5_6_7_8_9_10",
      "_11_12_13_14_15_16_1",
      "7_18_19_20_21_22_23_",
      "24_25_26",
      "$ "
    )
    session.assertCursorPosition(3, 6)
  }

  @Test
  fun mainBufferResizeWidthScenario2() = session(100, 5) { session ->
    session.write("$ cat long.txt")
    session.crlf()
    session.write("1_2_3_4_5_6_7_8_9_10_11_12_13_14_15_16_17_18_19_20_21_22_23_24_25_26_27_28_30")
    session.crlf()
    session.crlf()
    session.write("$ ")
    session.assertCursorPosition(3, 4)
    session.assertScrollbackLines()

    session.resize(6, 4)

    session.assertScrollbackLines(
      "$ cat ",
      "long.t",
      "xt",
      "1_2_3_",
      "4_5_6_",
      "7_8_9_",
      "10_11_",
      "12_13_",
      "14_15_",
      "16_17_",
      "18_19_",
      "20_21_",
      "22_23_",
      "24_25_")
    session.assertScreenLines("26_27_", "28_30", "", "$ ")
    session.assertCursorPosition(3, 4)
  }

  @Test
  fun mainBufferPointsTrackingDuringResize() = session(10, 4) { session ->
    session
      .write("line1").crlf()
      .write("line2").crlf()
      .write("line3").crlf()
      .write("line4")
    session.assertCursorPosition(6, 4)

    session.resize(5, 4)

    session.assertScrollbackLines("line1")
    session.assertScreenLines("line2", "line3", "line4")
    session.assertCursorPosition(1, 4)
  }

  @Test
  fun mainBufferResizeWidthIncrease() = session(5, 5) { session ->
    session
      .write("lin1").crlf()
      .write("lin2").crlf()
      .write("lin")
    session.assertCursorPosition(4, 3)

    session.resize(10, 5)

    assertThat(session.scrollbackRowCount()).isZero()
    session.assertScreenLines("lin1", "lin2", "lin")
    session.assertCursorPosition(4, 3)
  }

  @Test
  fun mainBufferResizeWidthDecrease() = session(10, 5) { session ->
    session
      .write("line_one").crlf()
      .write("line_two").crlf()
      .write("line_thre").crlf()
    session.assertCursorPosition(1, 4)

    session.resize(5, 5)

    session.assertScrollbackLines("line_", "one")
    session.assertScreenLines("line_", "two", "line_", "thre")
    session.assertCursorPosition(1, 5)
  }

  @Test
  fun mainBufferResizeBothDimensionsIncrease() = session(5, 5) { session ->
    session
      .write("lin1").crlf()
      .write("lin2").crlf()
      .write("lin3").crlf()
      .write("lin4").crlf()
      .write("lin")
    session.assertCursorPosition(4, 5)

    session.resize(10, 8)

    assertThat(session.scrollbackRowCount()).isZero()
    session.assertScreenLines("lin1", "lin2", "lin3", "lin4", "lin")
    session.assertCursorPosition(4, 5)
  }

  @Test
  fun mainBufferResizeBothDimensionsDecrease() = session(10, 8) { session ->
    session
      .write("first_line").crlf()
      .write("second_lin").crlf()
      .write("third_line").crlf()
      .write("fourth_lin").crlf()
      .write("fifth_line").crlf()
      .write("sixth_line").crlf()
    session.assertCursorPosition(1, 7)

    session.resize(5, 4)

    session.assertScrollbackLines(
      "first", "_line", "secon", "d_lin", "third", "_line", "fourt", "h_lin", "fifth")
    session.assertScreenLines("_line", "sixth", "_line")
    session.assertCursorPosition(1, 4)
  }

  // ===================== alternate buffer (truncate/extend, no reflow) =====================

  @Test
  fun altBufferResizeWidthIncrease() = session(5, 5) { session ->
    session.useAlternateBuffer(true)
    session
      .write("lin1").crlf()
      .write("lin2").crlf()
      .write("lin")
    session.assertCursorPosition(4, 3)

    session.resize(10, 5)

    assertThat(session.scrollbackRowCount()).isZero()
    session.assertScreenLines("lin1", "lin2", "lin")
    session.assertCursorPosition(4, 3)
  }

  @Test
  fun altBufferResizeWidthDecrease() = session(10, 5) { session ->
    session.useAlternateBuffer(true)
    session
      .write("line_one_A").crlf()
      .write("line_two_B").crlf()
      .write("line_thre")
    session.assertCursorPosition(10, 3)

    session.resize(5, 5)

    assertThat(session.scrollbackRowCount()).isZero()
    session.assertScreenLines("line_", "line_", "line_")
    session.assertCursorPosition(5, 3)
  }

  @Test
  fun altBufferResizeHeightIncrease() = session(5, 5) { session ->
    session.useAlternateBuffer(true)
    session
      .write("lin1").crlf()
      .write("lin2").crlf()
      .write("lin3").crlf()
      .write("lin")
    session.assertCursorPosition(4, 4)

    session.resize(5, 8)

    assertThat(session.scrollbackRowCount()).isZero()
    session.assertScreenLines("lin1", "lin2", "lin3", "lin")
    session.assertCursorPosition(4, 4)
  }

  @Test
  fun altBufferResizeHeightDecrease() = session(5, 8) { session ->
    session.useAlternateBuffer(true)
    session
      .write("lin1").crlf()
      .write("lin2").crlf()
      .write("lin3").crlf()
      .write("lin4").crlf()
      .write("lin5").crlf()
      .write("lin")
    session.assertCursorPosition(4, 6)

    session.resize(5, 4)

    assertThat(session.scrollbackRowCount()).isZero()
    // ENGINE-SPECIFIC: on an alternate-screen height shrink the emulator keeps the rows around the
    // cursor (the bottom) so the cursor stays visible. The alt screen never reflows or scrolls back,
    // so this is only a retention choice — the running app would redraw anyway.
    session.assertScreenLines("lin3", "lin4", "lin5", "lin")
    session.assertCursorPosition(4, 4)
  }

  @Test
  fun altBufferResizeBothDimensionsIncrease() = session(5, 5) { session ->
    session.useAlternateBuffer(true)
    session
      .write("AAA").crlf()
      .write("BBB").crlf()
      .write("CC")
    session.assertCursorPosition(3, 3)

    session.resize(10, 8)

    assertThat(session.scrollbackRowCount()).isZero()
    session.assertScreenLines("AAA", "BBB", "CC")
    session.assertCursorPosition(3, 3)
  }

  @Test
  fun altBufferResizeBothDimensionsDecrease() = session(10, 8) { session ->
    session.useAlternateBuffer(true)
    session
      .write("0123456789").crlf()
      .write("1123456789").crlf()
      .write("2123456789").crlf()
      .write("3123456789").crlf()
      .write("4123456789").crlf()
      .write("512345678")
    session.assertCursorPosition(10, 6)

    session.resize(5, 4)

    assertThat(session.scrollbackRowCount()).isZero()
    // ENGINE-SPECIFIC: the alt screen keeps the bottom (cursor) rows on a height shrink.
    // See altBufferResizeHeightDecrease.
    session.assertScreenLines("21234", "31234", "41234", "51234")
    session.assertCursorPosition(5, 4)
  }

  @Test
  fun altBufferResizeWidthIncreaseAndHeightDecrease() = session(5, 8) { session ->
    session.useAlternateBuffer(true)
    session
      .write("AAA").crlf()
      .write("BBB").crlf()
      .write("CCC").crlf()
      .write("DDD").crlf()
      .write("EEE").crlf()
      .write("FF")
    session.assertCursorPosition(3, 6)

    session.resize(10, 4)

    assertThat(session.scrollbackRowCount()).isZero()
    // ENGINE-SPECIFIC: the alt screen keeps the bottom (cursor) rows on a height shrink.
    // See altBufferResizeHeightDecrease.
    session.assertScreenLines("CCC", "DDD", "EEE", "FF")
    session.assertCursorPosition(3, 4)
  }

  @Test
  fun altBufferResizeWidthDecreaseAndHeightIncrease() = session(10, 4) { session ->
    session.useAlternateBuffer(true)
    session
      .write("0123456789").crlf()
      .write("1123456789").crlf()
      .write("212345678")
    session.assertCursorPosition(10, 3)

    session.resize(5, 8)

    assertThat(session.scrollbackRowCount()).isZero()
    session.assertScreenLines("01234", "11234", "21234")
    session.assertCursorPosition(5, 3)
  }

  // ===================== main-alternate-main switching =====================

  @Test
  fun altMainSwitchWidthChangeDuringAltBuffer() = session(10, 5) { session ->
    session
      .write("main_line1").crlf()
      .write("main_line2").crlf()
      .write("main_line")
    session.assertCursorPosition(10, 3)

    session.saveCursor()
    session.useAlternateBuffer(true)
    session.write("alt_content")

    session.resize(5, 5)

    session.restoreCursor()
    session.restoreCursor()
    session.useAlternateBuffer(false)

    session.assertScrollbackLines("main_")
    session.assertScreenLines("line1", "main_", "line2", "main_", "line")
    // ENGINE-SPECIFIC: the restored cursor position reflects how DECSC/DECRC interacts with the DEC
    // 1049 alt-screen save/restore, compounded by the doubled restoreCursor above.
    session.assertCursorPosition(1, 4)
  }

  @Test
  fun altMainSwitchHeightChangeDuringAltBuffer() = session(10, 8) { session ->
    session
      .write("line1").crlf()
      .write("line2").crlf()
      .write("line3").crlf()
      .write("line4").crlf()
      .write("line5").crlf()
      .write("line")
    session.assertCursorPosition(5, 6)

    session.saveCursor()
    session.useAlternateBuffer(true)
    session.write("alt_data")

    session.resize(10, 4)

    session.restoreCursor()
    session.useAlternateBuffer(false)

    session.assertScrollbackLines("line1", "line2")
    session.assertScreenLines("line3", "line4", "line5", "line")
    session.assertCursorPosition(5, 4)
  }

  @Test
  fun altMainSwitchBothDimensionsChangeDuringAltBuffer() = session(10, 8) { session ->
    session
      .write("first_line").crlf()
      .write("second_lin").crlf()
      .write("third_line").crlf()
      .write("fourth_lin").crlf()
      .write("fifth_line").crlf()
      .write("sixth_lin")
    session.assertCursorPosition(10, 6)

    session.saveCursor()
    session.useAlternateBuffer(true)
    session.write("alternate")

    session.resize(5, 4)

    session.restoreCursor()
    session.useAlternateBuffer(false)

    session.assertScrollbackLines(
      "first", "_line", "secon", "d_lin", "third", "_line", "fourt", "h_lin")
    session.assertScreenLines("fifth", "_line", "sixth", "_lin")
    // ENGINE-SPECIFIC: the restored cursor position differs for the same reason as
    // altMainSwitchWidthChangeDuringAltBuffer.
    session.assertCursorPosition(1, 3)
  }

  @Test
  fun altMainSwitchMultipleResizesDuringAltBuffer() = session(10, 5) { session ->
    session
      .write("main_lin1").crlf()
      .write("main_lin2").crlf()
      .write("main_lin3")
    session.assertCursorPosition(10, 3)

    session.saveCursor()
    session.useAlternateBuffer(true)
    session.write("alt")

    session.resize(8, 3)
    session.resize(6, 6)
    session.resize(5, 4)

    session.restoreCursor()
    session.useAlternateBuffer(false)

    // ENGINE-SPECIFIC: as in mainBufferResizeToSmallerHeightAndBack, rows moved to scrollback are
    // retained after the final grow (not pulled back into the screen), which anchors the screen and
    // cursor as asserted below.
    session.assertScrollbackLines("main_", "lin1", "main_")
    session.assertScreenLines("lin2", "main_", "lin3")
    session.assertCursorPosition(2, 3)
  }

  // ===================== reflow of soft-wrapped lines (libvterm scenarios) =====================
  //
  // Ported from libvterm's `t/69screen_reflow.test` (MIT, © Paul Evans): a soft-wrapped line must re-split
  // at every width, and rejoin once the screen is wide enough to hold it. [TerminalRow.wrapped] (libvterm's
  // `cont` line info) is asserted alongside the text: a row that wraps into the next must say so, and
  // rejoining the two on a widening resize must clear it.

  @Test
  fun reflowJoinsWrappedLineWhenWidened() = session(10, 5) { session ->
    session.write("A".repeat(12)) // 10 on row 0, 2 on row 1

    session.assertScreenRow(0, "AAAAAAAAAA")
    session.assertScreenRow(1, "AA")
    assertThat(session.screenLine(0).wrapped).describedAs("row 0 soft-wrapped").isTrue()
    session.assertCursorPosition(3, 2)

    session.resize(15, 5)

    session.assertScreenRow(0, "AAAAAAAAAAAA")
    session.assertScreenRow(1, "")
    assertThat(session.screenLine(0).wrapped).describedAs("row 0 rejoined, no longer wrapped").isFalse()
    session.assertCursorPosition(13, 1)

    session.resize(20, 5) // widening further changes nothing

    session.assertScreenRow(0, "AAAAAAAAAAAA")
    session.assertScreenRow(1, "")
    assertThat(session.screenLine(0).wrapped).describedAs("row 0 still not wrapped").isFalse()
    session.assertCursorPosition(13, 1)
  }

  @Test
  fun reflowSplitsLineIntoContinuationsWhenNarrowed() = session(10, 5) { session ->
    session.write("ABCDEFGHI")

    session.assertScreenRow(0, "ABCDEFGHI")
    assertThat(session.screenLine(0).wrapped).describedAs("row 0 fits, not wrapped").isFalse()
    session.assertCursorPosition(10, 1)

    session.resize(8, 5)

    session.assertScreenRow(0, "ABCDEFGH")
    session.assertScreenRow(1, "I")
    assertThat(session.screenLine(0).wrapped).describedAs("row 0 soft-wrapped").isTrue()
    session.assertCursorPosition(2, 2)

    session.resize(6, 5)

    session.assertScreenRow(0, "ABCDEF")
    session.assertScreenRow(1, "GHI")
    assertThat(session.screenLine(0).wrapped).describedAs("row 0 soft-wrapped").isTrue()
    session.assertCursorPosition(4, 2)
  }

  /**
   * The shell-prompt scenario: a wrapped line above the prompt must re-split at every width without the
   * prompt drifting, and must rejoin into a single row once the screen is wide enough to hold it.
   */
  @Test
  fun reflowOfAWrappedLineAboveThePrompt() = session(10, 5) { session ->
    session.write("PROMPT GOES HERE\r\n> \r\n\r\nPROMPT GOES HERE\r\n> ")

    session.expectFullRebuild() // the writes above scrolled the screen
    session.assertScreenRow(2, "PROMPT GOE")
    session.assertScreenRow(3, "S HERE")
    assertThat(session.screenLine(2).wrapped).describedAs("row 2 soft-wrapped").isTrue()
    session.assertScreenRow(4, "> ")
    session.assertScrollbackLines("PROMPT GOE", "S HERE") // the scrolled-off copy, wrapped the same way
    session.assertCursorPosition(3, 5)

    session.resize(11, 5)

    session.assertScreenRow(2, "PROMPT GOES")
    session.assertScreenRow(3, " HERE")
    assertThat(session.screenLine(2).wrapped).describedAs("row 2 soft-wrapped").isTrue()
    session.assertScreenRow(4, "> ")
    session.assertScrollbackLines("PROMPT GOES", " HERE") // scrollback re-splits along with the screen
    session.assertCursorPosition(3, 5)

    session.resize(12, 5)

    session.assertScreenRow(2, "PROMPT GOES ")
    session.assertScreenRow(3, "HERE")
    assertThat(session.screenLine(2).wrapped).describedAs("row 2 soft-wrapped").isTrue()
    session.assertScreenRow(4, "> ")
    session.assertScrollbackLines("PROMPT GOES ", "HERE")
    session.assertCursorPosition(3, 5)

    session.resize(16, 5)

    // ENGINE-SPECIFIC: rejoining the wrapped lines frees two rows, and this engine fills them by pulling
    // the (now unwrapped) history back onto the screen, emptying scrollback. libvterm expects the rows to
    // stay where they were and the screen to gain a blank row at the bottom instead — but its harness has
    // no scrollback to pull from, so the two are not really in conflict.
    session.assertScreenRow(0, "PROMPT GOES HERE")
    assertThat(session.screenLine(0).wrapped).describedAs("the whole line fits now").isFalse()
    session.assertScreenRow(1, "> ")
    session.assertScreenRow(2, "")
    session.assertScreenRow(3, "PROMPT GOES HERE")
    session.assertScreenRow(4, "> ")
    session.assertScrollbackLines()
    session.assertCursorPosition(3, 5)
  }

  /**
   * Shrinking to a single column must not lose the cursor: every row holds one character, so the content
   * reflows into far more rows than fit, and the cursor still has to land inside the screen.
   * See https://github.com/neovim/neovim/pull/21124 for the libvterm-side context.
   */
  @Test
  fun cursorStaysOnScreenWhenNarrowedToOneColumn() = session(5, 5) { session ->
    session.resize(1, 3)
    session.write(csi("2;1H") + "abc\r\n" + csi("H"))

    session.resize(1, 1)

    session.assertCursorPosition(1, 1)
  }

  // Two resize concerns are intentionally out of scope here, because they are not emulator behavior:
  //  * a minimum-width clamp — clamping a resize to some minimum column count is a UI policy; the
  //    engine honors any requested width, so there is nothing to assert at this layer.
  //  * selection-coordinate remapping across a resize — a text selection is owned by the frontend/UI,
  //    not the emulator, so the engine does no point-tracking and there is nothing to assert here.

}
