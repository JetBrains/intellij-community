// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Tests for [TerminalEmulator.paletteColor]: the 256-entry palette accessor. It reports the live
 * palette (default xterm values, plus any program `OSC 4` overrides / `OSC 104` resets) and is the
 * palette against which extended [TerminalColor.IndexedExtended] colors resolve to [TerminalColor.Rgb].
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
  fun rejectsOutOfRangeIndex() = session(4, 1) { session ->
    assertThatThrownBy { session.paletteColor(-1) }.isInstanceOf(IllegalArgumentException::class.java)
    assertThatThrownBy { session.paletteColor(256) }.isInstanceOf(IllegalArgumentException::class.java)
  }
}
