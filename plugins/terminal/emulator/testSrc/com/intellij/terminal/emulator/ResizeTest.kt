// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.EnumSource

/**
 * Resize and reflow across the main/alternate screens, driven through the [TerminalEmulator] API.
 * Each test feeds VT bytes (including a resize) and reads the resulting screen + scrollback back.
 *
 * Unlike the plain VT operations in [TextBufferTest], resize-time reflow is not a standardized VT
 * operation: the engine has its own reflow and row-anchoring algorithm, so the asserted wrapping,
 * scrollback split and cursor position describe *this engine's* behavior. Comments marked
 * "ENGINE-SPECIFIC" flag the spots where that is a deliberate engine choice rather than a universal
 * rule.
 *
 * [ScrollbackPullPolicy] only affects a resize that grows rows or widens the screen. [Common] holds
 * scenarios where all three policies agree; the other nested classes hold the ones that don't.
 */
class ResizeTest {

  @Nested
  @ParameterizedClass
  @EnumSource(ScrollbackPullPolicy::class)
  inner class Common(private val policy: ScrollbackPullPolicy) {

    @Test
    fun resizeReflowsAndKeepsRendering() = session(20, 5) { session ->
      session.setResizeScrollbackPullPolicy(policy)
      session.write("abcdefghij")
      session.resize(5, 5)
      session.assertScreenLines("abcde", "fghij")
    }

    // ===================== main buffer: height-only resize =====================

    @Test
    fun mainBufferResizeToBiggerHeight() = session(5, 5) { session ->
      session.setResizeScrollbackPullPolicy(policy)
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
      session.setResizeScrollbackPullPolicy(policy)
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
    fun mainBufferResizeToSmallerHeightAndKeepCursorVisible() = session(10, 4) { session ->
      session.setResizeScrollbackPullPolicy(policy)
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
    fun mainBufferClearAndResizeVertically() = session(10, 4) { session ->
      session.setResizeScrollbackPullPolicy(policy)
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
      session.setResizeScrollbackPullPolicy(policy)
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
      session.setResizeScrollbackPullPolicy(policy)
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
      session.setResizeScrollbackPullPolicy(policy)
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
      session.setResizeScrollbackPullPolicy(policy)
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
      session.setResizeScrollbackPullPolicy(policy)
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
      session.setResizeScrollbackPullPolicy(policy)
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
      session.setResizeScrollbackPullPolicy(policy)
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
      session.setResizeScrollbackPullPolicy(policy)
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
      session.setResizeScrollbackPullPolicy(policy)
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
      session.setResizeScrollbackPullPolicy(policy)
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
      session.setResizeScrollbackPullPolicy(policy)
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
      session.setResizeScrollbackPullPolicy(policy)
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
      // ENGINE-SPECIFIC: alt-screen shrink keeps the rows around the cursor, not the top ones.
      session.assertScreenLines("lin3", "lin4", "lin5", "lin")
      session.assertCursorPosition(4, 4)
    }

    @Test
    fun altBufferResizeBothDimensionsIncrease() = session(5, 5) { session ->
      session.setResizeScrollbackPullPolicy(policy)
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
      session.setResizeScrollbackPullPolicy(policy)
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
      // ENGINE-SPECIFIC: see altBufferResizeHeightDecrease.
      session.assertScreenLines("21234", "31234", "41234", "51234")
      session.assertCursorPosition(5, 4)
    }

    @Test
    fun altBufferResizeWidthIncreaseAndHeightDecrease() = session(5, 8) { session ->
      session.setResizeScrollbackPullPolicy(policy)
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
      // ENGINE-SPECIFIC: see altBufferResizeHeightDecrease.
      session.assertScreenLines("CCC", "DDD", "EEE", "FF")
      session.assertCursorPosition(3, 4)
    }

    @Test
    fun altBufferResizeWidthDecreaseAndHeightIncrease() = session(10, 4) { session ->
      session.setResizeScrollbackPullPolicy(policy)
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
      session.setResizeScrollbackPullPolicy(policy)
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
      // ENGINE-SPECIFIC: DECSC/DECRC interacts with the alt-screen save/restore here.
      session.assertCursorPosition(1, 4)
    }

    @Test
    fun altMainSwitchHeightChangeDuringAltBuffer() = session(10, 8) { session ->
      session.setResizeScrollbackPullPolicy(policy)
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
      session.setResizeScrollbackPullPolicy(policy)
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
      // ENGINE-SPECIFIC: see altMainSwitchWidthChangeDuringAltBuffer.
      session.assertCursorPosition(1, 3)
    }

    // ===================== reflow of soft-wrapped lines (libvterm scenarios) =====================
    // Ported from libvterm's `t/69screen_reflow.test` (MIT, © Paul Evans).

    @Test
    fun reflowJoinsWrappedLineWhenWidened() = session(10, 5) { session ->
      session.setResizeScrollbackPullPolicy(policy)
      session.write("A".repeat(12)) // 10 on row 0, 2 on row 1

      session.assertScreenRow(0, "AAAAAAAAAA")
      session.assertScreenRow(1, "AA")
      assertThat(session.screenLine(0).wrapped).isTrue()
      session.assertCursorPosition(3, 2)

      session.resize(15, 5)

      session.assertScreenRow(0, "AAAAAAAAAAAA")
      session.assertScreenRow(1, "")
      assertThat(session.screenLine(0).wrapped).isFalse()
      session.assertCursorPosition(13, 1)

      session.resize(20, 5)

      session.assertScreenRow(0, "AAAAAAAAAAAA")
      session.assertScreenRow(1, "")
      assertThat(session.screenLine(0).wrapped).isFalse()
      session.assertCursorPosition(13, 1)
    }

    @Test
    fun reflowSplitsLineIntoContinuationsWhenNarrowed() = session(10, 5) { session ->
      session.setResizeScrollbackPullPolicy(policy)
      session.write("ABCDEFGHI")

      session.assertScreenRow(0, "ABCDEFGHI")
      assertThat(session.screenLine(0).wrapped).isFalse()
      session.assertCursorPosition(10, 1)

      session.resize(8, 5)

      session.assertScreenRow(0, "ABCDEFGH")
      session.assertScreenRow(1, "I")
      assertThat(session.screenLine(0).wrapped).isTrue()
      session.assertCursorPosition(2, 2)

      session.resize(6, 5)

      session.assertScreenRow(0, "ABCDEF")
      session.assertScreenRow(1, "GHI")
      assertThat(session.screenLine(0).wrapped).isTrue()
      session.assertCursorPosition(4, 2)
    }

    // Narrowing to 1 column must not lose the cursor. See neovim/neovim#21124.
    @Test
    fun cursorStaysOnScreenWhenNarrowedToOneColumn() = session(5, 5) { session ->
      session.setResizeScrollbackPullPolicy(policy)
      session.resize(1, 3)
      session.write(csi("2;1H") + "abc\r\n" + csi("H"))

      session.resize(1, 1)

      session.assertCursorPosition(1, 1)
    }

    @Test
    fun wrapStraddlingHistoryBoundaryStillUnwraps() = session(3, 3) { session ->
      session.setResizeScrollbackPullPolicy(policy)
      // "E"x12 needs 4 rows at width 3: one in scrollback, three active — not fully in history
      // yet, so widening may still unwrap it. Only "AAA" (a whole line in scrollback) must stay.
      session.write("AAA").crlf().write("E".repeat(12))
      session.expectFullRebuild()
      session.assertScreenLines("EEE", "EEE", "EEE")

      session.resize(4, 3)

      session.assertScreenLines("EEEE", "EEEE", "EEEE")
      session.assertScrollbackLines("AAA")
    }

    // Out of scope: minimum-width clamping and selection remapping are UI/frontend concerns, not emulator behavior.
  }

  /** [ScrollbackPullPolicy.CURSOR_AT_BOTTOM]: the default — pulls only when the cursor is on the bottom row. */
  @Nested
  inner class CursorAtBottomPolicy {

    @Test
    fun mainBufferResizeInHeightWithScrolling() = session(5, 2) { session ->
      session.setResizeScrollbackPullPolicy(ScrollbackPullPolicy.CURSOR_AT_BOTTOM)
      // Let "line"/"line2" scroll off before growing.
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
    fun mainBufferResizeToSmallerHeightAndBack() = session(5, 5) { session ->
      session.setResizeScrollbackPullPolicy(ScrollbackPullPolicy.CURSOR_AT_BOTTOM)
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

      // ENGINE-SPECIFIC: unlike a row-only grow, this combined resize does not pull.
      assertThat(session.scrollbackRowCount()).isEqualTo(3)
      session.assertScrollbackLines("line", "line2", "line3")
      session.assertScreenLines("line4", "li")
      session.assertCursorPosition(3, 2)
    }

    @Test
    fun altMainSwitchMultipleResizesDuringAltBuffer() = session(10, 5) { session ->
      session.setResizeScrollbackPullPolicy(ScrollbackPullPolicy.CURSOR_AT_BOTTOM)
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

      // ENGINE-SPECIFIC: see mainBufferResizeToSmallerHeightAndBack.
      session.assertScrollbackLines("main_", "lin1", "main_")
      session.assertScreenLines("lin2", "main_", "lin3")
      session.assertCursorPosition(2, 3)
    }

    // Policy-sensitive despite no row-count change: widening enough to rejoin the wrapped line frees a
    // row, which this policy fills by pulling scrollback back. See NeverPolicy.wideningDoesNotUnwrapIntoHistory.
    @Test
    fun reflowOfAWrappedLineAboveThePrompt() = session(10, 5) { session ->
      session.setResizeScrollbackPullPolicy(ScrollbackPullPolicy.CURSOR_AT_BOTTOM)
      session.write("PROMPT GOES HERE\r\n> \r\n\r\nPROMPT GOES HERE\r\n> ")

      session.expectFullRebuild() // the writes above scrolled the screen
      session.assertScreenRow(2, "PROMPT GOE")
      session.assertScreenRow(3, "S HERE")
      assertThat(session.screenLine(2).wrapped).isTrue()
      session.assertScreenRow(4, "> ")
      session.assertScrollbackLines("PROMPT GOE", "S HERE")
      session.assertCursorPosition(3, 5)

      session.resize(11, 5)

      session.assertScreenRow(2, "PROMPT GOES")
      session.assertScreenRow(3, " HERE")
      assertThat(session.screenLine(2).wrapped).isTrue()
      session.assertScreenRow(4, "> ")
      session.assertScrollbackLines("PROMPT GOES", " HERE")
      session.assertCursorPosition(3, 5)

      session.resize(12, 5)

      session.assertScreenRow(2, "PROMPT GOES ")
      session.assertScreenRow(3, "HERE")
      assertThat(session.screenLine(2).wrapped).isTrue()
      session.assertScreenRow(4, "> ")
      session.assertScrollbackLines("PROMPT GOES ", "HERE")
      session.assertCursorPosition(3, 5)

      session.resize(16, 5)

      // ENGINE-SPECIFIC: pulls scrollback to fill the freed rows; see NeverPolicy for the alternative.
      session.assertScreenRow(0, "PROMPT GOES HERE")
      assertThat(session.screenLine(0).wrapped).isFalse()
      session.assertScreenRow(1, "> ")
      session.assertScreenRow(2, "")
      session.assertScreenRow(3, "PROMPT GOES HERE")
      session.assertScreenRow(4, "> ")
      session.assertScrollbackLines()
      session.assertCursorPosition(3, 5)
    }
  }

  /** [ScrollbackPullPolicy.NEVER]: never pulls, no matter the cursor. What Windows ConPTY needs. */
  @Nested
  inner class NeverPolicy {

    @Test
    fun mainBufferResizeInHeightWithScrollingKeepsHistory() = session(5, 2) { session ->
      session.setResizeScrollbackPullPolicy(ScrollbackPullPolicy.NEVER)
      // Same shape as CursorAtBottomPolicy's version, but NEVER must not pull.
      session
        .write("line").crlf()
        .write("line2").crlf()
        .write("line3").crlf()
        .write("li")
      session.assertCursorPosition(3, 2)

      session.resize(10, 5)

      assertThat(session.scrollbackRowCount()).isEqualTo(2)
      session.assertScrollbackLines("line", "line2")
      session.assertScreenLines("line3", "li")
      session.assertCursorPosition(3, 2)
    }

    @Test
    fun rowGrowthWithColumnChangeKeepsHistory() = session(6, 2) { session ->
      session.setResizeScrollbackPullPolicy(ScrollbackPullPolicy.NEVER)
      // Cols reflow before rows grow (dispatch order), so the policy must hold through both steps.
      session
        .write("AAAAAAAAA").crlf()
        .write("BBB").crlf()
        .write("CCC").crlf()
        .write("DDD")

      session.resize(4, 4)

      session.assertScreenLines("CCC", "DDD")
      session.assertScrollbackLines("AAAA", "AAAA", "A", "BBB")
    }

    @Test
    fun wideningDoesNotUnwrapIntoHistory() = session(10, 5) { session ->
      session.setResizeScrollbackPullPolicy(ScrollbackPullPolicy.NEVER)
      // Same shape as CursorAtBottomPolicy.reflowOfAWrappedLineAboveThePrompt.
      session.write("PROMPT GOES HERE\r\n> \r\n\r\nPROMPT GOES HERE\r\n> ")
      session.expectFullRebuild()
      session.assertScrollbackLines("PROMPT GOE", "S HERE")
      session.assertCursorPosition(3, 5)

      session.resize(11, 5)
      session.resize(12, 5)

      // Widening to 16 rejoins "PROMPT GOES HERE" (the default pulls scrollback here); NEVER must
      // keep row 0 as "> " and leave scrollback alone.
      session.resize(16, 5)

      session.assertScreenRow(0, "> ")
      session.assertScreenRow(1, "")
      session.assertScreenRow(2, "PROMPT GOES HERE")
      assertThat(session.screenLine(2).wrapped).isFalse()
      session.assertScreenRow(3, "> ")
      session.assertScreenRow(4, "")
      session.assertScrollbackLines("PROMPT GOES HERE")
      session.assertCursorPosition(3, 4)
    }
  }

  /** [ScrollbackPullPolicy.ALWAYS]: always pulls, even with the cursor off the bottom row. */
  @Nested
  inner class AlwaysPolicy {

    @Test
    fun mainBufferResizePullsEvenWithCursorNotAtBottom() = session(5, 2) { session ->
      session.setResizeScrollbackPullPolicy(ScrollbackPullPolicy.ALWAYS)
      session
        .write("line").crlf()
        .write("line2").crlf()
        .write("line3").crlf()
        .write("li")
      session.assertCursorPosition(3, 2)

      // Move the cursor off the bottom row, where the default policy would not pull.
      session.cursorPosition(1, 1)

      session.resize(10, 5)

      assertThat(session.scrollbackRowCount()).isZero()
      session.assertScreenLines("line", "line2", "line3", "li")
      session.assertCursorPosition(1, 3)
    }
  }

  // Two resize concerns are intentionally out of scope here, because they are not emulator behavior:
  //  * a minimum-width clamp — clamping a resize to some minimum column count is a UI policy; the
  //    engine honors any requested width, so there is nothing to assert at this layer.
  //  * selection-coordinate remapping across a resize — a text selection is owned by the frontend/UI,
  //    not the emulator, so the engine does no point-tracking and there is nothing to assert here.
}
