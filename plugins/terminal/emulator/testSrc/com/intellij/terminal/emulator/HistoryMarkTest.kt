// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [HistoryMark] — the "append the newly scrolled-off lines" primitive a document-backed renderer (e.g.
 * an editor) uses instead of [TerminalEmulator.takeChanges], which reports the whole viewport as
 * changed on every scroll. These tests drive the emulator directly (not through the session's
 * incremental mirror) because the mark is a separate incremental channel from [ScreenChange].
 */
class HistoryMarkTest {

  /**
   * The everyday case: filling the screen finalizes nothing; scrolling finalizes the lines pushed off
   * the top; those are the newest scrollback lines; and [HistoryMark.reset] re-anchors the boundary.
   */
  @Test
  fun finalizedLinesAreTheNewestScrollbackLines() = session(20, 4) { session ->
    val emulator = session.emulator
    emulator.markHistoryBoundary().use { mark ->
      assertThat(mark.finalizedLineCount()).isZero()

      // Fill the 4-row screen exactly: no scroll, nothing finalized.
      session.writeLinesWithCrlf(listOf("a", "b", "c", "d"))
      assertThat(mark.finalizedLineCount()).isZero()

      // Three more lines scroll a, b, c off the top into scrollback.
      session.crlf().write("e")
      session.crlf().write("f")
      session.crlf().write("g")
      assertThat(mark.finalizedLineCount()).isEqualTo(3)
      assertThat(mark.newlyFinalized(emulator)).containsExactly("a", "b", "c")

      // After reset the count restarts from the current boundary.
      mark.reset()
      assertThat(mark.finalizedLineCount()).isZero()

      session.crlf().write("h")
      session.crlf().write("i")
      assertThat(mark.finalizedLineCount()).isEqualTo(2)
      assertThat(mark.newlyFinalized(emulator)).containsExactly("d", "e")
    }
  }

  /**
   * The reason the mark exists: streaming output like `cat large.txt` scrolls far more lines than the
   * bounded scrollback can hold, so a [TerminalEmulator.scrollbackRows] delta plateaus and under-counts.
   * Polling the mark every line reconstructs every scrolled-off line, in order, past the eviction cap.
   */
  @Test
  fun reconstructsEveryScrolledOffLinePastScrollbackCap() = session(1_000, 3) { session ->
    // A wide terminal makes each scrollback row expensive (storage is charged per grid cell), so the
    // ~1 MiB scrollback floor holds only ~80 rows here — far fewer than the lines we are about to stream,
    // guaranteeing the oldest are evicted long before the run ends.
    val emulator = session.emulator
    val rows = emulator.size.rows
    val total = 2_000
    val appended = ArrayList<String>()

    emulator.markHistoryBoundary().use { mark ->
      for (i in 0 until total) {
        if (i > 0) session.crlf()
        session.write("line$i")
        // A renderer's per-frame step: append whatever just scrolled off, then re-anchor. Polling every
        // line means at most one line is finalized per poll, so the mark is never at risk of eviction.
        val finalized = mark.finalizedLineCount()
        assertThat(finalized).describedAs { "mark unexpectedly evicted at line $i" }.isNotNull().isNotNegative()
        appended.addAll(mark.newlyFinalized(emulator, checkNotNull(finalized)))
        mark.reset()
      }
    }

    // Every line except the last screenful (which is still live on the active screen) was finalized —
    // reconstructed exactly and in order, even though most were evicted from scrollback long ago.
    val expected = (0 until total - rows).map { "line$it" }
    assertThat(appended).isEqualTo(expected)

    // Confirm the cap actually evicted: scrollback retains only a fraction of what was finalized, so a
    // renderer relying on the scrollbackRows delta alone would have missed the rest.
    assertThat(emulator.scrollbackRows)
      .describedAs("expected the scrollback cap to have evicted lines")
      .isLessThan(expected.size)
  }

  // ---------------------------------------------------------------------------
  // Resize
  // ---------------------------------------------------------------------------
  //
  // A resize moves the history / active-screen boundary without a single write, so the mark reports a
  // count that no output produced: positive when rows drop into scrollback, negative when they are pulled
  // back onto the screen.

  /** A height shrink drops the rows that no longer fit into scrollback, exactly as a scroll would. */
  @Test
  fun heightShrinkFinalizesTheRowsItPushesOffTheScreen() = session(20, 20) { session ->
    val emulator = session.emulator
    // 20 lines fill the 20-row screen exactly, so nothing has scrolled yet.
    session.writeLinesWithCrlf((0 until 20).map { "R%02d".format(it) })

    emulator.markHistoryBoundary().use { mark ->
      assertThat(mark.finalizedLineCount()).isZero()
      assertThat(emulator.scrollbackRows).isZero()

      session.resize(20, 5)

      assertThat(mark.finalizedLineCount()).isEqualTo(15)
      assertThat(mark.newlyFinalized(emulator)).isEqualTo((0 until 15).map { "R%02d".format(it) })
    }
  }

  /** The mirror image: growing the screen pulls those rows back out of scrollback to fill it. */
  @Test
  fun heightGrowthRecoveringRowsReportsANegativeCount() = session(20, 5) { session ->
    val emulator = session.emulator
    // 20 lines on a 5-row screen: 15 scroll off into scrollback.
    session.writeLinesWithCrlf((0 until 20).map { "R%02d".format(it) })

    emulator.markHistoryBoundary().use { mark ->
      assertThat(mark.finalizedLineCount()).isZero()

      session.resize(20, 20)

      assertThat(mark.finalizedLineCount()).isEqualTo(-15)
      assertThat(emulator.scrollbackRows).describedAs("all 15 rows are back on the screen").isZero()
    }
  }

  /**
   * A width shrink reflows every line into more rows. The mark follows the old screen top, so the count
   * says how far the *visible screen* expanded — not how much output arrived, which is zero. A consumer
   * that reads it as new output misjudges this by a wide margin.
   */
  @Test
  fun widthShrinkReportsHowFarTheScreenExpanded() = session(40, 4) { session ->
    val emulator = session.emulator
    // Ten 40-character lines take one row each: six scroll off, four stay on the screen.
    session.writeLinesWithCrlf((0 until 10).map { "L$it".padEnd(40, '-') })

    emulator.markHistoryBoundary().use { mark ->
      assertThat(mark.finalizedLineCount()).isZero()

      // At 10 columns each line takes four rows, so the four screen rows become 16.
      session.resize(10, 4)

      assertThat(mark.finalizedLineCount()).isEqualTo(12)
    }
  }

  /** The mirror image: a width growth joins rows, so the screen contracts and the count goes negative. */
  @Test
  fun widthGrowthJoiningRowsReportsANegativeCount() = session(10, 4) { session ->
    val emulator = session.emulator
    // Ten 20-character lines take two rows each at 10 columns: 16 rows scroll off, four stay.
    session.writeLinesWithCrlf((0 until 10).map { "L$it".padEnd(20, '-') })

    emulator.markHistoryBoundary().use { mark ->
      assertThat(mark.finalizedLineCount()).isZero()

      // At 40 columns each line fits one row, so the four screen rows become two.
      session.resize(40, 4)

      assertThat(mark.finalizedLineCount()).isEqualTo(-2)
    }
  }

  // ---------------------------------------------------------------------------
  // The alternate screen
  // ---------------------------------------------------------------------------

  /**
   * The mark pins a line of the screen that was active when it was created or last [HistoryMark.reset].
   * [TerminalEmulator.scrollbackRows] reports the screen that is active *now*. On the alternate screen
   * the two describe different screens, so the count means nothing there.
   *
   * The count is exact again the moment the primary screen is active, so a consumer skips the mark
   * while the alternate screen is up instead of trying to correct it.
   */
  @Test
  fun finalizedLineCountIsNotUsableOnTheAlternateScreen() = session(20, 5) { session ->
    val emulator = session.emulator
    session.writeLinesWithCrlf((0 until 20).map { "R%02d".format(it) })

    emulator.markHistoryBoundary().use { mark ->
      assertThat(emulator.scrollbackRows).isEqualTo(15)
      assertThat(mark.finalizedLineCount()).isZero()

      session.useAlternateBuffer(true)

      assertThat(emulator.scrollbackRows).describedAs("the alternate screen retains no scrollback").isZero()
      assertThat(mark.finalizedLineCount())
        .describedAs("a meaningless count: the primary scrollback, negated")
        .isEqualTo(-15)

      session.useAlternateBuffer(false)

      assertThat(mark.finalizedLineCount()).describedAs("exact again on the primary screen").isZero()
    }
  }

  /**
   * The primary screen takes no writes while the alternate screen is up, but a resize still reflows it.
   * The mark therefore reports that reflow on the first read after the switch back, and nothing of what
   * the full-screen program drew.
   */
  @Test
  fun markReportsOnlyThePrimaryReflowAfterAnAlternateScreenExcursion() = session(20, 5) { session ->
    val emulator = session.emulator
    session.writeLinesWithCrlf((0 until 20).map { "R%02d".format(it) })

    emulator.markHistoryBoundary().use { mark ->
      session.useAlternateBuffer(true)
      session.writeLinesWithCrlf((0 until 30).map { "ALT$it" })
      // The resize reflows both screens; only the primary one has a scrollback boundary to move.
      session.resize(20, 10)
      session.useAlternateBuffer(false)

      // The 20 lines now fit a 10-row screen, so 10 stay in scrollback: the growth recovered 5 rows.
      assertThat(emulator.scrollbackRows).isEqualTo(10)
      assertThat(mark.finalizedLineCount()).isEqualTo(-5)
    }
  }
}

/** The `n` newest scrollback lines — the ones just reported finalized by [HistoryMark.finalizedLineCount]. */
private fun HistoryMark.newlyFinalized(
  emulator: TerminalEmulator,
  n: Int = checkNotNull(finalizedLineCount()) { "the mark was unexpectedly evicted" },
): List<String> {
  val scrollbackRows = emulator.scrollbackRows
  return (scrollbackRows - n until scrollbackRows).map { emulator.scrollbackLine(it).toStyledText().text }
}
