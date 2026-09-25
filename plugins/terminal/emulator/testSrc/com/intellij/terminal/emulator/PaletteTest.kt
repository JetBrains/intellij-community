// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Tests for [TerminalEmulator.paletteColor]: the 256-entry palette accessor. It reports the live
 * palette (default xterm values, plus any program `OSC 4` overrides / `OSC 104` resets) and is the
 * palette against which extended [TerminalColor.IndexedExtended] colors resolve to [TerminalColor.Rgb].
 * The embedder sets the defaults of the ANSI slots `0..15` with [TerminalEmulator.setDefaultAnsiColors].
 */
class PaletteTest {

  @Test
  fun defaultsMatchXtermCube() = session(4, 1) { session ->
    assertThat(session.paletteColor(16)).isEqualTo(TerminalColor.Rgb(0, 0, 0))        // cube origin
    assertThat(session.paletteColor(46)).isEqualTo(TerminalColor.Rgb(0, 255, 0))      // green1
    assertThat(session.paletteColor(196)).isEqualTo(TerminalColor.Rgb(255, 0, 0))     // red1
    assertThat(session.paletteColor(231)).isEqualTo(TerminalColor.Rgb(255, 255, 255)) // cube corner
  }

  @Test
  fun osc4OverridesExtendedSlot() = session(4, 1) { session ->
    session.write(osc("4;200;#123456"))
    assertThat(session.paletteColor(200)).isEqualTo(TerminalColor.Rgb(0x12, 0x34, 0x56))
  }

  /** OSC 4 on an ANSI slot (0..15) is observable here, even though such cells surface as IndexedAnsi. */
  @Test
  fun osc4OverridesAnsiSlot() = session(4, 1) { session ->
    session.write(osc("4;5;#0a141e"))
    assertThat(session.paletteColor(5)).isEqualTo(TerminalColor.Rgb(0x0A, 0x14, 0x1E))
  }

  @Test
  fun osc104ResetsToDefault() = session(4, 1) { session ->
    val original = session.paletteColor(200)
    session.write(osc("4;200;#123456"))
    assertThat(session.paletteColor(200)).isEqualTo(TerminalColor.Rgb(0x12, 0x34, 0x56))

    session.write(osc("104;200")) // reset slot 200
    assertThat(session.paletteColor(200)).isEqualTo(original)
  }

  /**
   * An extended color on a cell is a *live reference*: the cell value stays [TerminalColor.IndexedExtended]
   * and resolving it through [TerminalEmulator.paletteColor] reflects a later OSC 4 change rather than
   * a frozen snapshot.
   */
  @Test
  fun extendedCellColorIsLiveReference() = session(4, 1) { session ->
    session.write(csi("38;5;200m") + "X")
    assertThat(session.screenLine(0).cells[0].style.foreground).isEqualTo(TerminalColor.IndexedExtended(200))

    session.write(osc("4;200;#123456"))
    // The cell still holds the same reference...
    assertThat(session.screenLine(0).cells[0].style.foreground).isEqualTo(TerminalColor.IndexedExtended(200))
    // ...but resolving it now yields the overridden color.
    assertThat(session.paletteColor(200)).isEqualTo(TerminalColor.Rgb(0x12, 0x34, 0x56))
  }

  @Test
  fun embedderDefaultAnsiColorsAreReported() = session(4, 1) { session ->
    session.setDefaultAnsiColors(ansiColors(0x10))

    assertThat(session.paletteColor(0)).isEqualTo(TerminalColor.Rgb(0x10, 0x20, 0x30))
    assertThat(session.paletteColor(15)).isEqualTo(TerminalColor.Rgb(0x1F, 0x2F, 0x3F))
    // The extended slots keep the xterm defaults.
    assertThat(session.paletteColor(16)).isEqualTo(TerminalColor.Rgb(0, 0, 0))
    assertThat(session.paletteColor(231)).isEqualTo(TerminalColor.Rgb(255, 255, 255))

    session.write(osc("4;1;?"))
    session.assertResponses(osc("4;1;rgb:1111/2121/3131"))
  }

  /**
   * A program override has priority over the embedder default, also over a default set after the override.
   * After the program resets the override (OSC 104), the slot reports the latest embedder default.
   */
  @Test
  fun programOverrideOfAnsiSlotHasPriorityOverEmbedderDefault() = session(4, 1) { session ->
    session.setDefaultAnsiColors(ansiColors(0x10))
    session.write(osc("4;1;#aabbcc"))
    session.setDefaultAnsiColors(ansiColors(0x40))
    assertThat(session.paletteColor(1)).isEqualTo(TerminalColor.Rgb(0xAA, 0xBB, 0xCC))
    assertThat(session.paletteColor(2)).isEqualTo(TerminalColor.Rgb(0x42, 0x52, 0x62))

    session.write(osc("104;1"))
    assertThat(session.paletteColor(1)).isEqualTo(TerminalColor.Rgb(0x41, 0x51, 0x61))
  }

  @Test
  fun rejectsWrongAnsiColorCount() = session(4, 1) { session ->
    assertThatThrownBy { session.setDefaultAnsiColors(ansiColors(0x10).dropLast(1)) }.isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun rejectsOutOfRangeIndex() = session(4, 1) { session ->
    assertThatThrownBy { session.paletteColor(-1) }.isInstanceOf(IllegalArgumentException::class.java)
    assertThatThrownBy { session.paletteColor(256) }.isInstanceOf(IllegalArgumentException::class.java)
  }

  /** 16 ANSI colors; slot `n` is `(base + n, base + 0x10 + n, base + 0x20 + n)`. */
  private fun ansiColors(base: Int): List<TerminalColor.Rgb> =
    (0 until 16).map { TerminalColor.Rgb(base + it, base + 0x10 + it, base + 0x20 + it) }
}
