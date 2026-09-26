// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty

import com.intellij.terminal.emulator.session
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled("The expected scrollback row counts are OS-dependent: the test is passed on macOS, but on Linux/Windows")
class GhosttyScrollbackBufferTest {
  @Test
  fun `maxScrollbackBytes=1 is rounded up to one page`() = session(10, 2, maxScrollbackBytes = 1) { session ->
    // A tiny positive maxScrollbackBytes is rounded up to the minimum
    // storage (~two pages), so the scrolled-off lines are kept.
    session.writeLinesWithCrlf((1..8).map { "line_$it" })
    session.expectFullRebuild()
    session.assertScreenLines("line_7", "line_8")
    session.assertScrollbackLines((1..6).map { "line_$it" })
  }

  @Test
  fun `scrollback rows sawtooth (ASCII, one page)`() {
    // 1_228_799 is the last byte budget that still buys only the minimum two storage pages (one page of
    // history): a page is 409_600 bytes, so a third page is only allocated from 3 * 409_600 = 1_228_800 on.
    val maxScrollbackBytesList = listOf(1, 1_228_799)
    for (maxScrollbackBytes in maxScrollbackBytesList) {
      assertScrollbackSawtoothWithAscii(
        maxScrollbackBytes = maxScrollbackBytes,
        terminalWidth = 100,
        maxScrollbackLineCount = 944,
        afterMaxScrollbackLineCount = 471,
      )
      assertScrollbackSawtoothWithAscii(
        maxScrollbackBytes = maxScrollbackBytes,
        terminalWidth = 50,
        maxScrollbackLineCount = 1874,
        afterMaxScrollbackLineCount = 936,
      )
      assertScrollbackSawtoothWithAscii(
        maxScrollbackBytes = maxScrollbackBytes,
        terminalWidth = 10,
        maxScrollbackLineCount = 8710,
        afterMaxScrollbackLineCount = 4354,
      )
      assertScrollbackSawtoothWithAscii(
        maxScrollbackBytes = maxScrollbackBytes,
        terminalWidth = 1,
        maxScrollbackLineCount = 47928,
        afterMaxScrollbackLineCount = 23963,
      )
    }
  }

  @Test
  fun `scrollback rows sawtooth (ASCII, two pages)`() {
    // The whole third-page bracket: from 3 * 409_600 up to one byte short of a fourth page.
    val maxScrollbackBytesList = listOf(1_228_800, 1_638_399)
    for (maxScrollbackBytes in maxScrollbackBytesList) {
      assertScrollbackSawtoothWithAscii(
        maxScrollbackBytes = maxScrollbackBytes,
        terminalWidth = 100,
        maxScrollbackLineCount = 1418,
        afterMaxScrollbackLineCount = 945,
      )
      assertScrollbackSawtoothWithAscii(
        maxScrollbackBytes = maxScrollbackBytes,
        terminalWidth = 50,
        maxScrollbackLineCount = 2813,
        afterMaxScrollbackLineCount = 1875,
      )
      assertScrollbackSawtoothWithAscii(
        maxScrollbackBytes = maxScrollbackBytes,
        terminalWidth = 10,
        maxScrollbackLineCount = 13067,
        afterMaxScrollbackLineCount = 8711,
      )
    }
  }

  @Disabled("Slow and doesn't provide much value")
  @Test
  fun `scrollback rows sawtooth (wide CJK, one page)`() {
    // Wide (double-width) CJK rows fill each row with half as many characters as the ASCII test -- 50
    // fullwidth glyphs to ASCII's 100 -- yet occupy the same number of grid cells, so they retain exactly
    // the same rows as `scrollback rows sawtooth (ASCII, one page)` at each width. Scrollback is charged
    // per cell (bytes), not per character.
    val textGenerator = WideCjkTextGenerator()
    val maxScrollbackBytesList = listOf(1, 1_228_799)
    for (maxScrollbackBytes in maxScrollbackBytesList) {
      assertScrollbackSawtooth(
        maxScrollbackBytes = maxScrollbackBytes,
        terminalWidth = 100,
        maxScrollbackLineCount = 944,
        afterMaxScrollbackLineCount = 471,
        textGenerator = textGenerator,
      )
      assertScrollbackSawtooth(
        maxScrollbackBytes = maxScrollbackBytes,
        terminalWidth = 50,
        maxScrollbackLineCount = 1874,
        afterMaxScrollbackLineCount = 936,
        textGenerator = textGenerator,
      )
      assertScrollbackSawtooth(
        maxScrollbackBytes = maxScrollbackBytes,
        terminalWidth = 10,
        maxScrollbackLineCount = 8710,
        afterMaxScrollbackLineCount = 4354,
        textGenerator = textGenerator,
      )
    }
  }

  @Disabled("Slow and doesn't provide much value")
  @Test
  fun `scrollback rows sawtooth (grapheme clusters, two pages)`() {
    // Uses the same two-page byte budget as `scrollback rows sawtooth (ASCII, two pages)`, and the same
    // 100/50/10-cell rows with the same character count per row -- but every character carries a U+0301
    // combining mark. The extra code point per cell is charged against the engine's grapheme store, whose
    // row capacity does not grow with maxScrollbackBytes: so under this larger budget the grapheme rows
    // stay capped at the one-page count (944 at width 100) while plain ASCII grows to 1418 -- retention is
    // bounded by storage, not by character or cell count. (At the one-page budget every row kind is
    // cell-limited to the same 944, so the gap only appears above it.) The grid reads back the full
    // grapheme cluster, so the retained content round-trips the written combining-mark text.
    val textGenerator = GraphemeClusterTextGenerator()
    val maxScrollbackBytesList = listOf(1_228_800, 1_638_399)
    for (maxScrollbackBytes in maxScrollbackBytesList) {
      assertScrollbackSawtooth(
        maxScrollbackBytes = maxScrollbackBytes,
        terminalWidth = 100,
        maxScrollbackLineCount = 944,
        afterMaxScrollbackLineCount = 471,
        textGenerator = textGenerator,
      )
      assertScrollbackSawtooth(
        maxScrollbackBytes = maxScrollbackBytes,
        terminalWidth = 50,
        maxScrollbackLineCount = 1874,
        afterMaxScrollbackLineCount = 936,
        textGenerator = textGenerator,
      )
      assertScrollbackSawtooth(
        maxScrollbackBytes = maxScrollbackBytes,
        terminalWidth = 10,
        maxScrollbackLineCount = 8710,
        afterMaxScrollbackLineCount = 4354,
        textGenerator = textGenerator,
      )
    }
  }

  private fun assertScrollbackSawtoothWithAscii(
    maxScrollbackBytes: Int,
    terminalWidth: Int,
    maxScrollbackLineCount: Int,
    afterMaxScrollbackLineCount: Int,
  ) {
    assertScrollbackSawtooth(
      maxScrollbackBytes = maxScrollbackBytes,
      terminalWidth = terminalWidth,
      maxScrollbackLineCount = maxScrollbackLineCount,
      afterMaxScrollbackLineCount = afterMaxScrollbackLineCount,
      textGenerator = AsciiTextGenerator(),
    )
  }

  /**
   * Verifies libghostty-vt's page-pruning behavior. Writes [totalLinesPushedToScrollback] rows -- each
   * produced by [textGenerator] and exactly [terminalWidth] cells wide -- into a [terminalWidth]-wide,
   * 4-row emulator capped at [maxScrollbackBytes] and, after EVERY row, asserts the retained scrollback
   * count: it climbs one row per line up to [maxScrollbackLineCount] (a whole storage page has filled),
   * then a page is pruned and it drops to [afterMaxScrollbackLineCount], and the cycle repeats. Finally, it
   * asserts the exact screen and scrollback *content* -- proving pruning keeps precisely the most-recent
   * window of rows, in order.
   *
   * Each row's written text ([TextGenerator.generateTextOfWidth]) reads back verbatim through the grid, so
   * the content assertions compare against the same generator output.
   */
  private fun assertScrollbackSawtooth(
    maxScrollbackBytes: Int,
    terminalWidth: Int,
    maxScrollbackLineCount: Int,
    afterMaxScrollbackLineCount: Int,
    totalLinesPushedToScrollback: Int = 100_000,
    textGenerator: TextGenerator,
  ) {
    val terminalHeight = 4

    session(terminalWidth, terminalHeight, maxScrollbackBytes = maxScrollbackBytes) { session ->
      session.writeLinesWithCrlf((1..terminalHeight).map { "x" }) // fill the screen buffer
      var prevScrollbackRows = 0
      assertThat(session.emulator.scrollbackRows).isZero()

      var maxReachedCount = 0
      (1..totalLinesPushedToScrollback).forEach { id ->
        session.crlf()
        session.write(textGenerator.generateTextOfWidth(terminalWidth, id))
        val expectedScrollbackRows = if (prevScrollbackRows == maxScrollbackLineCount) {
          maxReachedCount++
          afterMaxScrollbackLineCount
        }
        else {
          prevScrollbackRows + 1
        }
        assertThat(session.emulator.scrollbackRows)
          .describedAs { "scrollback row count after writing row #$id" }
          .isEqualTo(expectedScrollbackRows)
        prevScrollbackRows = expectedScrollbackRows
      }
      assertThat(maxReachedCount).isGreaterThanOrEqualTo(2)

      val screenTopId = totalLinesPushedToScrollback - terminalHeight + 1
      session.expectFullRebuild()
      session.assertScreenLines((screenTopId until screenTopId + terminalHeight).map { textGenerator.generateTextOfWidth(terminalWidth, it) })
      val scrollbackTopId = screenTopId - prevScrollbackRows
      session.assertScrollbackLines((scrollbackTopId until scrollbackTopId + prevScrollbackRows).map { textGenerator.generateTextOfWidth(terminalWidth, it) })
    }
  }

  // The next three tests use one emulator each and the SAME 100x4 screen, 2 MB limit, and 2500 written
  // rows, so their retained-row counts are directly comparable. They show that scrollback is charged
  // per grid *cell*, not per UTF-8 byte: single-codepoint glyphs all cost the same regardless of
  // encoding, while a grapheme cluster (multiple codepoints in one cell) costs more. (The exact counts
  // are specific to the bundled libghostty-vt and may need updating if the engine changes.)

  @Test
  fun wideCjkRowsCostTheSameAsAscii() = session(100, 4, maxScrollbackBytes = 2_000_000) { session ->
    // U+4E2D is a wide CJK ideograph (3-byte UTF-8): 50 of them fill the same 100 cells as the ASCII
    // row, so the same 1549 rows are retained -- cost is per cell, not per UTF-8 byte.
    session.write(("中".repeat(50) + "\r\n").repeat(2_500))
    assertThat(session.scrollbackRowCount()).isEqualTo(1549)
  }

  @Test
  fun emojiRowsCostTheSameAsAscii() = session(100, 4, maxScrollbackBytes = 2_000_000) { session ->
    // U+1F600 is a supplementary-plane emoji (4-byte UTF-8, one wide cell pair): again 100 cells per
    // row, again 1549 rows retained -- the UTF-8 length is irrelevant.
    session.write(("😀".repeat(50) + "\r\n").repeat(2_500))
    assertThat(session.scrollbackRowCount()).isEqualTo(1549)
  }

  @Test
  fun graphemeClusterRowsRetainFewer() = session(100, 4, maxScrollbackBytes = 2_000_000) { session ->
    // Each cell here holds a grapheme cluster ('a' + U+0301 combining acute = two codepoints), which
    // needs extra storage beyond the fixed per-cell cost. Under the SAME 2 MB limit only 601 rows are
    // kept -- far fewer than the 1549 of the single-codepoint tests above.
    session.write(("á".repeat(100) + "\r\n").repeat(2_500))
    assertThat(session.scrollbackRowCount()).isEqualTo(601)
  }

  /** Produces the per-row text the sawtooth tests write, sized so each row fills a fixed cell count. */
  private interface TextGenerator {
    /** The text written for row [id], occupying exactly [width] grid cells. */
    fun generateTextOfWidth(width: Int, id: Int): String
  }

  private class AsciiTextGenerator : TextGenerator {
    override fun generateTextOfWidth(width: Int, id: Int): String {
      var prefix = "${id}:"
      if (prefix.length >= width) {
        prefix = ""
      }
      val middle = CharArray(width - prefix.length) { 'a' + it % 26 }.concatToString()
      return (prefix + middle).also {
        assertThat(it).hasSize(width)
      }
    }
  }

  /**
   * Fills each row with fullwidth (double-width) BMP characters, so `width / 2` characters occupy exactly
   * `width` grid cells (requires an even `width`). The id prefix uses fullwidth digits (U+FF10..U+FF19)
   * + a fullwidth colon (U+FF1A); the filler cycles fullwidth 'Ａ'..'Ｚ' (U+FF21..U+FF3A). Each character
   * is a leading WIDE cell + a trailing SPACER and reads back verbatim.
   */
  private class WideCjkTextGenerator : TextGenerator {
    override fun generateTextOfWidth(width: Int, id: Int): String {
      require(width % 2 == 0) { "wide rows need an even width, was $width" }
      val charCount = width / 2
      var prefix = id.toString().map { Char(0xFF10 + (it - '0')) }.joinToString("") + '：' // fullwidth digits + '：'
      if (prefix.length >= charCount) {
        prefix = ""
      }
      val middle = CharArray(charCount - prefix.length) { Char(0xFF21 + it % 26) }.concatToString() // fullwidth 'Ａ'..'Ｚ'
      return (prefix + middle).also {
        assertThat(it).hasSize(charCount)
      }
    }
  }

  /**
   * Wraps an [AsciiTextGenerator]: a U+0301 combining acute accent is appended after each base character,
   * turning every cell into a two-codepoint grapheme cluster that still occupies a single cell (so its
   * `width` base characters still fill `width` cells). The engine stores the extra code point (costing extra storage
   * bytes) and surfaces it on read-back, so the presented text round-trips the written accented text.
   */
  private class GraphemeClusterTextGenerator : TextGenerator {
    private val ascii = AsciiTextGenerator()

    override fun generateTextOfWidth(width: Int, id: Int): String {
      val base = ascii.generateTextOfWidth(width, id)
      val sb = StringBuilder(base.length * 2)
      for (ch in base) {
        sb.append(ch)
        sb.append(Char(0x0301)) // U+0301 COMBINING ACUTE ACCENT
      }
      return sb.toString()
    }
  }
}
