// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Double-width (CJK / emoji) cell handling, checked at the cell level: a wide glyph occupies a
 * [CellWidth.WIDE] cell followed by a [CellWidth.SPACER] placeholder, so the check is engine- and
 * model-agnostic (no UI placeholder character).
 *
 * Lone (unpaired) UTF-16 surrogates are intentionally not covered: the emulator's input is a UTF-8
 * byte stream, which cannot encode an unpaired surrogate in the first place.
 */
class WideCharTest {

  @Test
  fun doubleWidth() = session(10, 2) { session ->
    session.write("生活習慣病")

    session.assertScreenLines("生活習慣病")
    // Every CJK glyph is a WIDE cell followed by a SPACER placeholder cell.
    assertThat(session.screenLine(0).contentWidths()).isEqualTo(widths(CellWidth.WIDE, CellWidth.SPACER, 5))
  }

  @Test
  fun emojiDoubleWidth() = session(10, 2) { session ->
    // Emoji-presentation characters (e.g. ✅ U+2705, ❌ U+274C) occupy two cells.
    session.write("✅a❌b")

    session.assertScreenLines("✅a❌b")
    assertThat(session.screenLine(0).contentWidths()).containsExactly(
      CellWidth.WIDE, CellWidth.SPACER, CellWidth.NARROW,
      CellWidth.WIDE, CellWidth.SPACER, CellWidth.NARROW)
  }

  @Test
  fun supplementaryEmojiDoubleWidth() = session(10, 2) { session ->
    // 😀 (U+1F600) is a supplementary-plane emoji occupying two cells (WIDE + SPACER).
    session.write("a😀b")

    session.assertScreenLines("a😀b")
    assertThat(session.screenLine(0).contentWidths()).containsExactly(
      CellWidth.NARROW, CellWidth.WIDE, CellWidth.SPACER, CellWidth.NARROW)
  }

  @Test
  fun mixedBmpAndSupplementaryDoubleWidth() = session(10, 2) { session ->
    // Both a BMP wide glyph (生) and a supplementary emoji (😀) occupy two cells.
    session.write("生😀")

    session.assertScreenLines("生😀")
    assertThat(session.screenLine(0).contentWidths()).isEqualTo(widths(CellWidth.WIDE, CellWidth.SPACER, 2))
  }

  @Test
  fun bmpDoubleWidthAfterSurrogatePair() = session(10, 2) { session ->
    // 😀 = two cells, 生 = two cells, four cells total.
    session.write("😀生")

    session.assertScreenLines("😀生")
    assertThat(session.screenLine(0).contentWidths()).isEqualTo(widths(CellWidth.WIDE, CellWidth.SPACER, 2))
  }

  @Test
  fun consecutiveSupplementaryEmoji() = session(10, 2) { session ->
    // Two supplementary emoji in a row: each occupies two cells.
    session.write("😀🚀")

    session.assertScreenLines("😀🚀")
    assertThat(session.screenLine(0).contentWidths()).isEqualTo(widths(CellWidth.WIDE, CellWidth.SPACER, 2))
  }

  /**
   * A wide glyph written over existing narrow cells claims two columns, so it replaces both `0` and `1`
   * of `0123`. Ported from libvterm's `t/61screen_unicode.test` (MIT, © Paul Evans).
   */
  @Test
  fun wideCharOverwritesTwoNarrowCells() = session(10, 2) { session ->
    session.write("0123" + csi("H")) // write, then home the cursor
    session.write("０")          // U+FF10 FULLWIDTH DIGIT ZERO

    session.assertScreenLines("０23")
    assertThat(session.screenLine(0).contentWidths()).containsExactly(
      CellWidth.WIDE, CellWidth.SPACER, CellWidth.NARROW, CellWidth.NARROW)
  }

  /**
   * A wide glyph that no longer fits in the last column wraps to the next row as a whole, leaving the
   * final cell of the previous row blank. Ported from libvterm's `t/61screen_unicode.test`.
   */
  @Test
  fun wideCharInLastColumnWrapsWhole() = session(80, 3) { session ->
    session.write(csi("80G") + "０") // to the last column, then a fullwidth glyph

    session.assertScreenRow(0, "")
    session.assertScreenRow(1, "０")
    assertThat(session.screenLine(0).cells[79].codePoint).describedAs("last cell of row 0 stays blank").isZero()
    assertThat(session.screenLine(1).cells[0].width).isEqualTo(CellWidth.WIDE)
    assertThat(session.screenLine(1).cells[1].width).isEqualTo(CellWidth.SPACER)
  }

  /** A list that repeats the pair ([a], [b]) [times] times. */
  private fun widths(a: CellWidth, b: CellWidth, times: Int): List<CellWidth> =
    (0 until times).flatMap { listOf(a, b) }
}
