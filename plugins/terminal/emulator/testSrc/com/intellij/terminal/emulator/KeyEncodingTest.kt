// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import com.jediterm.terminal.TerminalKeyEncoder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

/**
 * [TerminalEmulator.encodeKeyEvent]: key events -> the escape sequences a terminal sends to the PTY.
 *
 * Every case asserts the exact bytes against a golden expectation (xterm ctlseqs / the Kitty keyboard
 * protocol spec), and — where JediTerm encodes the same key — against JediTerm's own
 * [TerminalKeyEncoder] output, so the two engines cannot silently drift apart on the sequences shells
 * and TUIs rely on. Mode-dependent cases flip the mode on both sides: DECSET on the emulator, the
 * matching switch on the JediTerm encoder (see [KeyCase]).
 */
class KeyEncodingTest {

  // ---- editing keys ----

  @Test
  fun `enter, tab, backspace, escape`() = keys { k ->
    k.assertEncodes("\r", TerminalKey.ENTER, awtKey = KeyEvent.VK_ENTER)
    // No JediTerm cross-check for TAB: its encoder leaves TAB to the typed-character path (getCode returns null).
    k.assertEncodes("\t", TerminalKey.TAB)
    k.assertEncodes("\u007f", TerminalKey.BACKSPACE, awtKey = KeyEvent.VK_BACK_SPACE)
    // No JediTerm cross-check for ESC: its encoder table has no entry (handled elsewhere in jediterm).
    k.assertEncodes(ESC_STR, TerminalKey.ESCAPE)
  }

  @Test
  fun `printable keys pass their text through`() = keys { k ->
    k.assertEncodes("a", TerminalKey.A, text = "a", unshifted = 'a'.code)
    k.assertEncodes("A", TerminalKey.A, mods = setOf(TerminalInputModifier.SHIFT), text = "A", unshifted = 'a'.code)
    k.assertEncodes("1", TerminalKey.DIGIT_1, text = "1", unshifted = '1'.code)
    k.assertEncodes(" ", TerminalKey.SPACE, text = " ", unshifted = ' '.code)
  }

  @Test
  fun `ctrl chords produce control characters`() = keys { k ->
    k.assertEncodes("\u0001", TerminalKey.A, mods = setOf(TerminalInputModifier.CTRL), unshifted = 'a'.code)
    k.assertEncodes("\u001a", TerminalKey.Z, mods = setOf(TerminalInputModifier.CTRL), unshifted = 'z'.code)
    k.assertEncodes("\u0000", TerminalKey.SPACE, mods = setOf(TerminalInputModifier.CTRL), unshifted = ' '.code)
  }

  // ---- arrows and navigation ----

  @Test
  fun `arrow keys`() = keys { k ->
    k.assertEncodes(csi("A"), TerminalKey.ARROW_UP, awtKey = KeyEvent.VK_UP)
    k.assertEncodes(csi("B"), TerminalKey.ARROW_DOWN, awtKey = KeyEvent.VK_DOWN)
    k.assertEncodes(csi("C"), TerminalKey.ARROW_RIGHT, awtKey = KeyEvent.VK_RIGHT)
    k.assertEncodes(csi("D"), TerminalKey.ARROW_LEFT, awtKey = KeyEvent.VK_LEFT)
  }

  @Test
  fun `arrow keys in application cursor mode`() = keys { k ->
    k.applicationCursorKeys()
    k.assertEncodes(esc("OA"), TerminalKey.ARROW_UP, awtKey = KeyEvent.VK_UP)
    k.assertEncodes(esc("OB"), TerminalKey.ARROW_DOWN, awtKey = KeyEvent.VK_DOWN)
    k.assertEncodes(esc("OC"), TerminalKey.ARROW_RIGHT, awtKey = KeyEvent.VK_RIGHT)
    k.assertEncodes(esc("OD"), TerminalKey.ARROW_LEFT, awtKey = KeyEvent.VK_LEFT)
  }

  @Test
  fun `modified arrow keys`() = keys { k ->
    // Combos with ALT carry no JediTerm cross-check: JediTerm prefixes ESC (altSendsEscape) instead of
    // using xterm's CSI 1;<n> modifier encoding, so the engines legitimately differ there.
    val up = TerminalKey.ARROW_UP
    k.assertEncodes(csi("1;2A"), up, mods = setOf(TerminalInputModifier.SHIFT), awtKey = KeyEvent.VK_UP, awtMods = InputEvent.SHIFT_DOWN_MASK)
    k.assertEncodes(csi("1;3A"), up, mods = setOf(TerminalInputModifier.ALT))
    k.assertEncodes(csi("1;4A"), up, mods = setOf(TerminalInputModifier.SHIFT, TerminalInputModifier.ALT))
    k.assertEncodes(csi("1;5A"), up, mods = setOf(TerminalInputModifier.CTRL), awtKey = KeyEvent.VK_UP, awtMods = InputEvent.CTRL_DOWN_MASK)
    k.assertEncodes(csi("1;6A"), up, mods = setOf(TerminalInputModifier.SHIFT, TerminalInputModifier.CTRL),
                    awtKey = KeyEvent.VK_UP, awtMods = InputEvent.SHIFT_DOWN_MASK or InputEvent.CTRL_DOWN_MASK)
    k.assertEncodes(csi("1;7A"), up, mods = setOf(TerminalInputModifier.ALT, TerminalInputModifier.CTRL))
    k.assertEncodes(csi("1;8A"), up, mods = setOf(TerminalInputModifier.SHIFT, TerminalInputModifier.ALT, TerminalInputModifier.CTRL))
  }

  @Test
  fun `modified arrows use CSI even in application cursor mode`() = keys { k ->
    k.applicationCursorKeys()
    k.assertEncodes(csi("1;5A"), TerminalKey.ARROW_UP, mods = setOf(TerminalInputModifier.CTRL),
                    awtKey = KeyEvent.VK_UP, awtMods = InputEvent.CTRL_DOWN_MASK)
  }

  @Test
  fun `ctrl+left and ctrl+right jump over words`() = keys { k ->
    // Readline binds CSI 1;5D / CSI 1;5C to backward-word / forward-word, so these chords move by words.
    k.assertEncodes(csi("1;5D"), TerminalKey.ARROW_LEFT, mods = setOf(TerminalInputModifier.CTRL),
                    awtKey = KeyEvent.VK_LEFT, awtMods = InputEvent.CTRL_DOWN_MASK)
    k.assertEncodes(csi("1;5C"), TerminalKey.ARROW_RIGHT, mods = setOf(TerminalInputModifier.CTRL),
                    awtKey = KeyEvent.VK_RIGHT, awtMods = InputEvent.CTRL_DOWN_MASK)
  }

  @Test
  fun `cmd+left and cmd+right encode the meta modifier, not line moves`() = keys { k ->
    // "Cmd+arrows move to line start/end" is a macOS convention implemented above VT encoding:
    // JediTerm hardcodes Cmd+Left/Right -> Ctrl+A / Ctrl+E (readline line start/end) on macOS, and
    // the Ghostty app ships the same translation as default "natural text editing" keybinds — a
    // layer above libghostty-vt. The encoder only speaks the wire protocol, where SUPER is the
    // xterm meta modifier — a sequence shells ignore. Hence no JediTerm cross-check; the session
    // layer owns the Cmd+arrows translation, the same place that decides macos-option-as-alt.
    k.assertEncodes(csi("1;9D"), TerminalKey.ARROW_LEFT, mods = setOf(TerminalInputModifier.SUPER))
    k.assertEncodes(csi("1;9C"), TerminalKey.ARROW_RIGHT, mods = setOf(TerminalInputModifier.SUPER))
  }

  @Test
  fun `home and end`() = keys { k ->
    k.assertEncodes(csi("H"), TerminalKey.HOME, awtKey = KeyEvent.VK_HOME)
    k.assertEncodes(csi("F"), TerminalKey.END, awtKey = KeyEvent.VK_END)
    k.assertEncodes(csi("1;5H"), TerminalKey.HOME, mods = setOf(TerminalInputModifier.CTRL),
                    awtKey = KeyEvent.VK_HOME, awtMods = InputEvent.CTRL_DOWN_MASK)
  }

  @Test
  fun `home and end in application cursor mode`() = keys { k ->
    // No JediTerm cross-check: arrowKeysApplicationSequences() remaps only the arrows, so JediTerm
    // keeps CSI H / CSI F here while xterm (and ghostty) switch Home/End to SS3 with the cursor keys.
    k.applicationCursorKeys()
    k.assertEncodes(esc("OH"), TerminalKey.HOME)
    k.assertEncodes(esc("OF"), TerminalKey.END)
  }

  @Test
  fun `insert, delete, page up, page down`() = keys { k ->
    k.assertEncodes(csi("2~"), TerminalKey.INSERT, awtKey = KeyEvent.VK_INSERT)
    k.assertEncodes(csi("3~"), TerminalKey.DELETE, awtKey = KeyEvent.VK_DELETE)
    k.assertEncodes(csi("5~"), TerminalKey.PAGE_UP, awtKey = KeyEvent.VK_PAGE_UP)
    k.assertEncodes(csi("6~"), TerminalKey.PAGE_DOWN, awtKey = KeyEvent.VK_PAGE_DOWN)
    k.assertEncodes(csi("3;5~"), TerminalKey.DELETE, mods = setOf(TerminalInputModifier.CTRL),
                    awtKey = KeyEvent.VK_DELETE, awtMods = InputEvent.CTRL_DOWN_MASK)
  }

  // ---- function keys ----

  @Test
  fun `function keys`() = keys { k ->
    k.assertEncodes(esc("OP"), TerminalKey.F1, awtKey = KeyEvent.VK_F1)
    k.assertEncodes(esc("OQ"), TerminalKey.F2, awtKey = KeyEvent.VK_F2)
    k.assertEncodes(esc("OR"), TerminalKey.F3, awtKey = KeyEvent.VK_F3)
    k.assertEncodes(esc("OS"), TerminalKey.F4, awtKey = KeyEvent.VK_F4)
    k.assertEncodes(csi("15~"), TerminalKey.F5, awtKey = KeyEvent.VK_F5)
    k.assertEncodes(csi("17~"), TerminalKey.F6, awtKey = KeyEvent.VK_F6)
    k.assertEncodes(csi("18~"), TerminalKey.F7, awtKey = KeyEvent.VK_F7)
    k.assertEncodes(csi("19~"), TerminalKey.F8, awtKey = KeyEvent.VK_F8)
    k.assertEncodes(csi("20~"), TerminalKey.F9, awtKey = KeyEvent.VK_F9)
    k.assertEncodes(csi("21~"), TerminalKey.F10, awtKey = KeyEvent.VK_F10)
    k.assertEncodes(csi("23~"), TerminalKey.F11, awtKey = KeyEvent.VK_F11)
    k.assertEncodes(csi("24~"), TerminalKey.F12, awtKey = KeyEvent.VK_F12)
  }

  @Test
  fun `modified function keys`() = keys { k ->
    k.assertEncodes(csi("1;5P"), TerminalKey.F1, mods = setOf(TerminalInputModifier.CTRL),
                    awtKey = KeyEvent.VK_F1, awtMods = InputEvent.CTRL_DOWN_MASK)
    k.assertEncodes(csi("15;2~"), TerminalKey.F5, mods = setOf(TerminalInputModifier.SHIFT),
                    awtKey = KeyEvent.VK_F5, awtMods = InputEvent.SHIFT_DOWN_MASK)
  }

  // ---- events that produce nothing ----

  @Test
  fun `releases and bare modifiers produce nothing in legacy mode`() = keys { k ->
    k.assertEncodes("", TerminalKey.ARROW_UP, action = TerminalKeyAction.RELEASE)
    k.assertEncodes("", TerminalKey.SHIFT_LEFT)
    k.assertEncodes("", TerminalKey.CONTROL_LEFT)
  }

  @Test
  fun `composing events produce nothing`() = keys { k ->
    val event = TerminalKeyEvent(TerminalKey.A, text = "a", unshiftedCodepoint = 'a'.code, composing = true)
    assertThat(k.session.emulator.encodeKeyEvent(event)).isEmpty()
  }

  // ---- Kitty keyboard protocol ----

  @Test
  fun `kitty disambiguate mode changes escape and ctrl chords`() = keys { k ->
    k.session.write(csi(">1u")) // push "disambiguate escape codes"
    k.assertEncodes(csi("27u"), TerminalKey.ESCAPE)
    k.assertEncodes(csi("97;5u"), TerminalKey.A, mods = setOf(TerminalInputModifier.CTRL), unshifted = 'a'.code)

    k.session.write(csi("<u")) // pop back to legacy
    k.assertEncodes(ESC_STR, TerminalKey.ESCAPE)
  }

  @Test
  fun `kitty report-events mode encodes key releases`() = keys { k ->
    k.session.write(csi(">3u")) // disambiguate + report release events
    k.assertEncodes(csi("97;5u"), TerminalKey.A, mods = setOf(TerminalInputModifier.CTRL), unshifted = 'a'.code)
    k.assertEncodes(csi("97;5:3u"), TerminalKey.A, action = TerminalKeyAction.RELEASE,
                    mods = setOf(TerminalInputModifier.CTRL), unshifted = 'a'.code)
  }

  // ---- harness ----

  private fun keys(block: (KeyCase) -> Unit) = session(80, 24) { session -> block(KeyCase(session)) }

  /**
   * Drives both encoders for one test: the emulator under test and JediTerm's [TerminalKeyEncoder] as
   * the reference implementation. Mode switches go to both, so their outputs stay comparable.
   */
  private class KeyCase(val session: EmulatorTestSession) {
    private val jediterm = TerminalKeyEncoder()

    fun applicationCursorKeys() {
      session.write(csi("?1h"))
      jediterm.arrowKeysApplicationSequences()
    }

    /**
     * Asserts the emulator encodes the event to [expected], and that JediTerm agrees when [awtKey] is
     * given. Cases JediTerm does not encode (plain text, ctrl chords, Kitty) pass no [awtKey].
     */
    fun assertEncodes(
      expected: String,
      key: TerminalKey,
      action: TerminalKeyAction = TerminalKeyAction.PRESS,
      mods: Set<TerminalInputModifier> = emptySet(),
      text: String = "",
      unshifted: Int = 0,
      awtKey: Int? = null,
      awtMods: Int = 0,
    ) {
      val event = TerminalKeyEvent(key, action, mods, text, unshifted)
      val actual = session.emulator.encodeKeyEvent(event).toString(Charsets.ISO_8859_1)
      assertThat(actual.escaped())
        .describedAs("ghostty encoding of $key action=$action mods=$mods")
        .isEqualTo(expected.escaped())

      if (awtKey != null) {
        val jeditermBytes = jediterm.getCode(awtKey, awtMods)
        assertThat(jeditermBytes?.toString(Charsets.ISO_8859_1)?.escaped())
          .describedAs("jediterm encoding of $key mods=$mods (awt keyCode=$awtKey)")
          .isEqualTo(expected.escaped())
      }
    }
  }
}

/** Readable assertion output: control bytes as escapes instead of invisible characters. */
internal fun String.escaped(): String = buildString {
  for (ch in this@escaped) {
    when {
      ch == ESC_CHAR -> append("<ESC>")
      ch.code < 32 || ch.code == 127 -> append("\\x%02x".format(ch.code))
      else -> append(ch)
    }
  }
}
