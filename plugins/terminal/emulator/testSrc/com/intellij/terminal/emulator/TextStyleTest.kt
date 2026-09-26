// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * SGR parsing: the graphic attributes (bold, italic, underline, …) and the colors — true-color
 * (`38;2` / `48;2`) and 256-color (`38;5` / `48;5`) — must land on the cell's [CellStyle], accumulate
 * until reset, and be carried by an erase.
 *
 * The 16 ANSI colors (`0..15`) surface as [TerminalColor.IndexedAnsi] (the embedder themes them); the
 * extended palette (`16..255`) surfaces as [TerminalColor.IndexedExtended], a live reference resolved
 * against the engine palette on demand (see [TerminalEmulator.paletteColor]).
 *
 * The attribute-accumulation and erase scenarios are ported from libvterm's `t/64screen_pen.test`
 * (MIT, © Paul Evans); its expectations are restated against this API, which keeps palette colors
 * unresolved instead of reporting an RGB value.
 */
class TextStyleTest {

  @Test
  fun trueColorForeground() = session(12, 1) { session ->
    session.write(csi("38;2;0;128;0m") + "Hello")
    assertThat(foreground(session)).isEqualTo(TerminalColor.Rgb(0, 128, 0))
  }

  @Test
  fun trueColorBackground() = session(12, 1) { session ->
    session.write(csi("48;2;0;128;0m") + "Hello")
    assertThat(background(session)).isEqualTo(TerminalColor.Rgb(0, 128, 0))
  }

  @Test
  fun trueColorCombinedWithBold() = session(12, 1) { session ->
    session.write(csi("0;38;2;0;128;0;48;2;0;255;0;1m") + "Hello")
    val style = session.screenLine(0).cells[0].style
    assertThat(style.foreground).isEqualTo(TerminalColor.Rgb(0, 128, 0))
    assertThat(style.background).isEqualTo(TerminalColor.Rgb(0, 255, 0))
    assertThat(style.bold).isTrue()
  }

  /** The 16 ANSI colors surface as [TerminalColor.IndexedAnsi] for the embedder to theme. */
  @Test
  fun ansiColorIsIndexedAnsi() = session(12, 1) { session ->
    session.write(csi("38;5;9m") + "Hello") // bright red, palette index 9
    assertThat(foreground(session)).isEqualTo(TerminalColor.IndexedAnsi(9))
  }

  /** 256-color indices >= 16 surface as [TerminalColor.IndexedExtended] (a live palette reference). */
  @Test
  fun extendedColorForegroundIsIndexedExtended() = session(12, 1) { session ->
    session.write(csi("38;5;46m") + "Hello")
    assertThat(foreground(session)).isEqualTo(TerminalColor.IndexedExtended(46))
  }

  @Test
  fun extendedColorBackgroundIsIndexedExtended() = session(12, 1) { session ->
    session.write(csi("48;5;196m") + "Hello")
    assertThat(background(session)).isEqualTo(TerminalColor.IndexedExtended(196))
  }

  @Test
  fun extendedColorCombinedWithBold() = session(12, 1) { session ->
    session.write(csi("0;38;5;46;48;5;196;1m") + "Hello")
    val style = session.screenLine(0).cells[0].style
    assertThat(style.foreground).isEqualTo(TerminalColor.IndexedExtended(46))
    assertThat(style.background).isEqualTo(TerminalColor.IndexedExtended(196))
    assertThat(style.bold).isTrue()
  }

  /**
   * Graphic attributes accumulate as they are set and all drop at once on `CSI m`; each cell keeps the
   * pen it was written with.
   */
  @Test
  fun graphicAttributesAccumulateUntilReset() = session(12, 1) { session ->
    session.write("A")
    session.write(csi("1m") + "B")
    session.write(csi("3m") + "C")
    session.write(csi("4m") + "D")
    session.write(csi("m") + "E")

    val cells = session.screenLine(0).cells
    assertThat(cells[0].style).isEqualTo(CellStyle.Default)
    assertThat(cells[1].style).isEqualTo(CellStyle(bold = true))
    assertThat(cells[2].style).isEqualTo(CellStyle(bold = true, italic = true))
    assertThat(cells[3].style).isEqualTo(CellStyle(bold = true, italic = true, underline = Underline.SINGLE))
    assertThat(cells[4].style).isEqualTo(CellStyle.Default)
  }

  /**
   * SGR 4's colon sub-parameter (`CSI 4:2m` etc.) selects an underline style, not just
   * on/off.
   */
  @Test
  fun extendedUnderlineStylesMapToDistinctVariants() = session(12, 1) { session ->
    session.write(csi("4:2m") + "A")
    session.write(csi("4:3m") + "B")
    session.write(csi("4:4m") + "C")
    session.write(csi("4:5m") + "D")

    val cells = session.screenLine(0).cells
    assertThat(cells[0].style.underline).isEqualTo(Underline.DOUBLE)
    assertThat(cells[1].style.underline).isEqualTo(Underline.CURLY)
    assertThat(cells[2].style.underline).isEqualTo(Underline.DOTTED)
    assertThat(cells[3].style.underline).isEqualTo(Underline.DASHED)
  }

  /** The ANSI color SGRs (`30..37` / `40..47`) select palette slots `0..7`. */
  @Test
  fun ansiForegroundAndBackgroundSgr() = session(12, 1) { session ->
    session.write(csi("31m") + "G" + csi("m"))
    session.write(csi("42m") + "H" + csi("m"))

    val cells = session.screenLine(0).cells
    assertThat(cells[0].style.foreground).isEqualTo(TerminalColor.IndexedAnsi(1))
    assertThat(cells[0].style.background).isEqualTo(TerminalColor.Default)
    assertThat(cells[1].style.foreground).isEqualTo(TerminalColor.Default)
    assertThat(cells[1].style.background).isEqualTo(TerminalColor.IndexedAnsi(2))
  }

  /**
   * BCE (background color erase): an erase under a colored pen paints the erased cells with the pen's
   * background, dropping every other attribute (its foreground, its reverse flag). `CSI 7;33;44m` + EL
   * leaves cells with only a blue background — not yellow-on-blue, and not reversed.
   *
   * The engine stores such a cell as background-only, separate from the style map (see
   * `GhosttyCellContentTag.BG_COLOR_PALETTE` / `BG_COLOR_RGB`), so it carries no
   * foreground, bold, or other attribute. The written cell right next to them does carry the full pen,
   * so this is about what an erase stores, not about SGR parsing.
   */
  @Test
  fun eraseUnderAColoredPenPaintsOnlyItsBackground() = session(20, 2) { session ->
    session.write(csi("H") + csi("7;33;44m") + csi("K")) // reverse + yellow on blue, then erase to end of line

    val cells = session.screenLine(0).cells
    assertThat(cells[0].style).isEqualTo(CellStyle(background = TerminalColor.IndexedAnsi(4)))
    assertThat(cells[19].style).isEqualTo(CellStyle(background = TerminalColor.IndexedAnsi(4)))

    session.write("X") // the same pen, written rather than erased, is kept in full
    assertThat(session.screenLine(0).cells[0].style)
      .isEqualTo(CellStyle(foreground = TerminalColor.IndexedAnsi(3), background = TerminalColor.IndexedAnsi(4), inverse = true))
  }

  @Test
  fun backgroundOnlyCellsPastTheTextAreKept() = session(20, 1) { session ->
    session.write("abc" + csi("44m") + csi("K")) // "abc", then blue background to the end of the line

    val styled = session.screenLine(0).toStyledText()
    assertThat(styled.text).isEqualTo("abc" + " ".repeat(17))
    assertThat(styled.styleRanges).hasSize(1)
    val range = styled.styleRanges.single()
    assertThat(range.startOffset).isEqualTo(3)
    assertThat(range.endOffset).isEqualTo(20)
    assertThat(range.style).isEqualTo(CellStyle(background = TerminalColor.IndexedAnsi(4)))
  }

  @Test
  fun backgroundOnlyCellsWithATrueColorPenAreKept() = session(10, 1) { session ->
    session.write("Hi" + csi("48;2;10;20;30m") + csi("K"))

    val styled = session.screenLine(0).toStyledText()
    assertThat(styled.text).isEqualTo("Hi" + " ".repeat(8))
    assertThat(styled.styleRanges.single().style).isEqualTo(CellStyle(background = TerminalColor.Rgb(10, 20, 30)))
  }

  @Test
  fun backgroundOnlyCellsInTheMiddleOfARowAreKept() = session(20, 1) { session ->
    session.write("ab" + csi("44m"))
    session.eraseCharacters(3) // cols 2..4 (0-based): blue background, no text; cursor stays at column 2
    session.write(csi("m") + csi("5C") + "cd") // reset the pen, skip cols 5..6 unwritten, "cd" at 7..8

    val styled = session.screenLine(0).toStyledText()
    assertThat(styled.text).isEqualTo("ab" + " ".repeat(5) + "cd")
    assertThat(styled.styleRanges).hasSize(1)
    val range = styled.styleRanges.single()
    assertThat(range.startOffset).isEqualTo(2)
    assertThat(range.endOffset).isEqualTo(5)
    assertThat(range.style).isEqualTo(CellStyle(background = TerminalColor.IndexedAnsi(4)))
  }

  /**
   * DECSCNM (`CSI ?5h`) reverses the whole screen. The engine tracks it as a screen mode rather than as
   * per-cell state — this API surfaces no reverse-screen flag, so the mode is asserted through DECRQM
   * (`CSI ?5$p`), as libvterm's `t/64screen_pen.test` does alongside its cell checks.
   */
  @Test
  fun reverseScreenModeIsTracked() = session(12, 2) { session ->
    session.write("R" + csi("?5h"))
    session.write(csi($$"?5$p"))
    session.assertResponses(csi($$"?5;1$y")) // 1 = set

    session.write(csi("?5l"))
    session.write(csi($$"?5$p"))
    session.assertResponses(csi($$"?5;1$y"), csi($$"?5;2$y")) // 2 = reset
  }

  private fun foreground(session: EmulatorTestSession) = session.screenLine(0).cells[0].style.foreground
  private fun background(session: EmulatorTestSession) = session.screenLine(0).cells[0].style.background
}
