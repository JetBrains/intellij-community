// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Operating System Command (OSC) sequences the engine itself handles: the window title (OSC 0/2), the
 * default fg/bg color set + reset (OSC 11/111) and the color query (OSC 10/11 `?`), and OSC 8 hyperlinks.
 *
 * The JetBrains custom command channel (OSC 1341) is sniffed out of the raw [TerminalEmulator.write]
 * stream instead of being handled by the engine; it is covered by
 * [com.intellij.terminal.emulator.impl.ghostty.OscCustomCommandSnifferTest].
 */
class OscTest {

  @Test
  fun oscSetTitle() = session(30, 3) { session ->
    session.write(osc("0;Title A") + "Done1 ")
    assertThat(session.title).isEqualTo("Title A")

    session.write(osc("2;Title C") + "Done3")
    assertThat(session.title).isEqualTo("Title C")

    session.assertScreenLines("Done1 Done3")
  }

  /**
   * A program can silently recolor the terminal via OSC (a SET produces no pty reply and no listener
   * callback), so reading [TerminalEmulator.foregroundColor] / [TerminalEmulator.backgroundColor] is
   * the only way to observe the resulting *effective* color. This verifies an OSC 11 override follows
   * through to the getter and that OSC 111 resets back to the engine default.
   */
  @Test
  fun effectiveColorsFollowOscOverrides() = session(10, 10) { session ->
    // The getters report the effective color; with no override yet, that is the engine's default.
    val defaultFg = session.foregroundColor
    val defaultBg = session.backgroundColor

    // A program recolors the background via OSC 11 (silent SET): the effective background now follows
    // the override, while the untouched foreground stays at its default.
    session.write(osc("11;#aabbcc"))
    assertThat(session.backgroundColor).isEqualTo(TerminalColor.Rgb(0xAA, 0xBB, 0xCC))
    assertThat(session.foregroundColor).isEqualTo(defaultFg)

    // OSC 111 removes the override, so the effective background falls back to the default again.
    session.write(osc("111"))
    assertThat(session.backgroundColor).isEqualTo(defaultBg)
  }

  @Test
  fun oscQueryColors() = session(10, 10) { session ->
    session.write(osc("10;#100f0e")) // set foreground
    session.write(osc("11;#010203")) // set background
    session.write(osc("10;?"))        // query foreground
    session.write(osc("11;?"))        // query background
    session.assertResponses(osc("10;rgb:1010/0f0f/0e0e"), osc("11;rgb:0101/0202/0303"))
  }

  /** With no program override, the getters and the color query report the embedder default colors. */
  @Test
  fun oscQueryColorsReportsEmbedderDefaults() = session(10, 10) { session ->
    session.setDefaultForegroundColor(TerminalColor.Rgb(0x10, 0x0F, 0x0E))
    session.setDefaultBackgroundColor(TerminalColor.Rgb(0x01, 0x02, 0x03))
    assertThat(session.foregroundColor).isEqualTo(TerminalColor.Rgb(0x10, 0x0F, 0x0E))
    assertThat(session.backgroundColor).isEqualTo(TerminalColor.Rgb(0x01, 0x02, 0x03))

    // ST-terminated queries, as Codex sends them: the reply uses the terminator of the query.
    session.write(osc("10;?", OscTerminator.ST) + osc("11;?", OscTerminator.ST))
    session.assertResponses(osc("10;rgb:1010/0f0f/0e0e", OscTerminator.ST), osc("11;rgb:0101/0202/0303", OscTerminator.ST))
  }

  /**
   * A program override has priority over the embedder default, also over a default set after the override.
   * After the program resets the override (OSC 111), the query reports the latest embedder default.
   */
  @Test
  fun programOverrideHasPriorityOverEmbedderDefault() = session(10, 10) { session ->
    session.setDefaultBackgroundColor(TerminalColor.Rgb(0x01, 0x02, 0x03))
    session.write(osc("11;#aabbcc"))
    session.setDefaultBackgroundColor(TerminalColor.Rgb(0x04, 0x05, 0x06))
    session.write(osc("11;?"))

    session.write(osc("111"))
    session.write(osc("11;?"))

    session.assertResponses(osc("11;rgb:aaaa/bbbb/cccc"), osc("11;rgb:0404/0505/0606"))
  }

  /**
   * OSC 8 hyperlinks: text emitted between the start (`ESC]8;;<uri>`) and end (`ESC]8;;`) carries the
   * link URI; the engine surfaces it per-cell via [com.intellij.terminal.emulator.Cell.hyperlink].
   * Covers both the ST- and BEL-terminated forms.
   */
  @Test
  fun oscHyperlink() = session(40, 3) { session ->
    val foo = "https://example.com/foo"
    session.write(osc("8;;$foo", OscTerminator.ST) + "Foo link" + osc("8;;", OscTerminator.ST) + " Some text 1")
    session.crlf()
    val bar = "https://example.com/bar"
    session.write(osc("8;;$bar") + "Bar link" + osc("8;;") + " Some text 2")

    val line0 = session.screenLine(0).cells
    assertThat(line0[0].hyperlink).isEqualTo(foo)  // 'F' of "Foo link"
    assertThat(line0[7].hyperlink).isEqualTo(foo)  // 'k', last char of "Foo link"
    assertThat(line0[8].hyperlink).isNull()        // ' ' of " Some text 1"

    val line1 = session.screenLine(1).cells
    assertThat(line1[0].hyperlink).isEqualTo(bar)  // 'B' of "Bar link"
    assertThat(line1[7].hyperlink).isEqualTo(bar)  // 'k', last char of "Bar link"
    assertThat(line1[8].hyperlink).isNull()        // ' ' of " Some text 2"
  }

  /**
   * [com.intellij.terminal.emulator.TerminalRow.toStyledText] surfaces the link as a coalesced
   * [com.intellij.terminal.emulator.StyledText.hyperlinks] range over the row text — the form the
   * session layer forwards to the UI.
   */
  @Test
  fun oscHyperlinkInStyledText() = session(40, 3) { session ->
    val uri = "https://example.com/foo"
    session.write("pre " + osc("8;;$uri") + "LINK" + osc("8;;") + " post")

    val styled = session.screenLine(0).toStyledText()
    assertThat(styled.text).isEqualTo("pre LINK post")
    assertThat(styled.hyperlinks).containsExactly(HyperlinkRange("pre ".length, "pre LINK".length, uri))
  }
}
