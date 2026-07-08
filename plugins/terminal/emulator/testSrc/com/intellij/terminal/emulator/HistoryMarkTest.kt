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
        assertThat(finalized).describedAs { "mark unexpectedly evicted at line $i" }.isNotNegative()
        appended.addAll(mark.newlyFinalized(emulator, finalized))
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
}

/** The `n` newest scrollback lines — the ones just reported finalized by [HistoryMark.finalizedLineCount]. */
private fun HistoryMark.newlyFinalized(emulator: TerminalEmulator, n: Int = finalizedLineCount()): List<String> {
  val scrollbackRows = emulator.scrollbackRows
  return (scrollbackRows - n until scrollbackRows).map { emulator.scrollbackLine(it).toStyledText().text }
}
