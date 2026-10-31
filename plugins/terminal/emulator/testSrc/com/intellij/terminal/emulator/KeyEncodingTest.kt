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

  @Test
  fun `ctrl chords carry the character, so fixterms can tell them apart`() = keys { k ->
    // The text is the character without Ctrl, as the Ghostty hosts pass it; the encoder then picks a
    // control byte or a CSI u chord. No JediTerm cross-check: JediTerm sends the C0 byte AWT computed
    // for every chord, so Ctrl+Shift+M is CR there.
    val ctrl = setOf(TerminalInputModifier.CTRL)
    val ctrlShift = setOf(TerminalInputModifier.CTRL, TerminalInputModifier.SHIFT)
    k.assertEncodes("\u0003", TerminalKey.C, mods = ctrl, text = "c", unshifted = 'c'.code)
    k.assertEncodes(csi("109;6u"), TerminalKey.M, mods = ctrlShift, text = "M", unshifted = 'm'.code)
    k.assertEncodes("\u0000", TerminalKey.SPACE, mods = ctrlShift, text = " ", unshifted = ' '.code)
    k.assertEncodes(csi("59;5u"), TerminalKey.SEMICOLON, mods = ctrl, text = ";", unshifted = ';'.code)
    k.assertEncodes(csi("64;5u"), TerminalKey.DIGIT_2, mods = ctrlShift, text = "@", unshifted = '2'.code)
  }

  @Test
  fun `ctrl+i, ctrl+m and ctrl+bracket are CSI u with text and nothing without`() = keys { k ->
    // fixterms reserves these three for CSI u. Outside the Kitty protocol the session layer sends the
    // classic Tab, Enter and Escape bytes itself, so bash and friends keep working.
    val ctrl = setOf(TerminalInputModifier.CTRL)
    k.assertEncodes(csi("105;5u"), TerminalKey.I, mods = ctrl, text = "i", unshifted = 'i'.code)
    k.assertEncodes("", TerminalKey.I, mods = ctrl, unshifted = 'i'.code)
    k.assertEncodes("", TerminalKey.M, mods = ctrl, unshifted = 'm'.code)
    k.assertEncodes("", TerminalKey.BRACKET_LEFT, mods = ctrl, unshifted = '['.code)
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

  // ---- Alt chords ----

  @Test
  fun `alt chords get an ESC prefix in legacy mode when Option acts as Alt`() = keys { k ->
    // No JediTerm cross-check: it prefixes ESC above its encoder table.
    val alt = setOf(TerminalInputModifier.ALT)
    val altShift = setOf(TerminalInputModifier.ALT, TerminalInputModifier.SHIFT)
    k.session.emulator.setOptionAsAlt(true)
    k.assertEncodes(esc("f"), TerminalKey.F, mods = alt, text = "f", unshifted = 'f'.code)
    k.assertEncodes(esc("F"), TerminalKey.F, mods = altShift, text = "F", unshifted = 'f'.code)
    k.assertEncodes(esc(">"), TerminalKey.PERIOD, mods = altShift, text = ">", unshifted = '.'.code)

    // Only macOS has an Option key that composes text; elsewhere mode 1036 prefixes ESC regardless.
    k.session.emulator.setOptionAsAlt(false)
    val optionComposesText = System.getProperty("os.name").startsWith("Mac")
    k.assertEncodes(if (optionComposesText) "f" else esc("f"), TerminalKey.F, mods = alt, text = "f", unshifted = 'f'.code)
  }

  // ---- Kitty keyboard protocol ----

  @Test
  fun `kitty flags are readable, and alt chords become CSI u under them`() = keys { k ->
    assertThat(k.session.emulator.kittyKeyboardFlags).isEmpty()
    k.session.write(csi(">1u"))
    assertThat(k.session.emulator.kittyKeyboardFlags).containsExactly(KittyKeyboardFlag.DISAMBIGUATE_ESCAPE_CODES)
    k.session.emulator.setOptionAsAlt(true)
    k.assertEncodes(csi("102;3u"), TerminalKey.F, mods = setOf(TerminalInputModifier.ALT), text = "f", unshifted = 'f'.code)

    k.session.write(csi(">31u")) // every flag; the stack now holds two entries
    assertThat(k.session.emulator.kittyKeyboardFlags).containsExactlyInAnyOrderElementsOf(KittyKeyboardFlag.entries)
    k.session.write(csi("<u"))
    k.session.write(csi("<u"))
    assertThat(k.session.emulator.kittyKeyboardFlags).isEmpty()
  }

  @Test
  fun `kitty disambiguate mode changes escape and ctrl chords`() = keys { k ->
    k.session.write(csi(">1u")) // push "disambiguate escape codes"
    k.assertEncodes(csi("27u"), TerminalKey.ESCAPE)
    k.assertEncodes(csi("97;5u"), TerminalKey.A, mods = setOf(TerminalInputModifier.CTRL), unshifted = 'a'.code)

    k.session.write(csi("<u")) // pop back to legacy
    k.assertEncodes(ESC_STR, TerminalKey.ESCAPE)
  }

  @Test
  fun `kitty disambiguate mode types shifted characters when shift is consumed`() = keys { k ->
    k.session.write(csi(">1u"))
    val shift = setOf(TerminalInputModifier.SHIFT)
    k.assertEncodes("@", TerminalKey.UNIDENTIFIED, mods = shift, consumed = shift, text = "@", unshifted = '@'.code)
    k.assertEncodes("A", TerminalKey.UNIDENTIFIED, mods = shift, consumed = shift, text = "A", unshifted = 'a'.code)
    // An unconsumed shift makes the text a chord, which fish ignores (IJPL-255707).
    k.assertEncodes(csi("64;2u"), TerminalKey.UNIDENTIFIED, mods = shift, text = "@", unshifted = '@'.code)
  }

  @Test
  fun `kitty ctrl chords without text encode the unshifted codepoint`() = keys { k ->
    k.session.write(csi(">1u"))
    val ctrl = setOf(TerminalInputModifier.CTRL)
    k.assertEncodes(csi("105;5u"), TerminalKey.I, mods = ctrl, unshifted = 'i'.code)
    k.assertEncodes(csi("91;5u"), TerminalKey.BRACKET_LEFT, mods = ctrl, unshifted = '['.code)
  }

  @Test
  fun `kitty report-alternates reports the shifted character next to the unshifted key`() = keys { k ->
    val shift = setOf(TerminalInputModifier.SHIFT)
    k.session.write(csi(">5u")) // disambiguate + report alternates: plain text still wins for a consumed shift
    k.assertEncodes("@", TerminalKey.DIGIT_2, mods = shift, consumed = shift, text = "@", unshifted = '2'.code)
    k.session.write(csi(">13u")) // + report all: every key is a CSI u sequence
    k.assertEncodes(csi("50:64;2u"), TerminalKey.DIGIT_2, mods = shift, consumed = shift, text = "@", unshifted = '2'.code)
    k.assertEncodes(csi("97:65;2u"), TerminalKey.A, mods = shift, consumed = shift, text = "A", unshifted = 'a'.code)
  }

  @Test
  fun `kitty report-events mode encodes key releases`() = keys { k ->
    k.session.write(csi(">3u")) // disambiguate + report release events
    k.assertEncodes(csi("97;5u"), TerminalKey.A, mods = setOf(TerminalInputModifier.CTRL), unshifted = 'a'.code)
    k.assertEncodes(csi("97;5:3u"), TerminalKey.A, action = TerminalKeyAction.RELEASE,
                    mods = setOf(TerminalInputModifier.CTRL), unshifted = 'a'.code)
  }

  @Test
  fun `kitty report-events mode encodes repeats`() = keys { k ->
    k.session.write(csi(">3u")) // disambiguate + report release events
    k.assertEncodes(csi("97;5:2u"), TerminalKey.A, action = TerminalKeyAction.REPEAT, mods = setOf(TerminalInputModifier.CTRL), unshifted = 'a'.code)
    // A repeat of plain text stays plain text, like the press.
    k.assertEncodes("a", TerminalKey.A, action = TerminalKeyAction.REPEAT, text = "a", unshifted = 'a'.code)
  }

  @Test
  fun `kitty report-all mode encodes bare modifier keys`() = keys { k ->
    k.session.write(csi(">1u"))
    k.assertEncodes("", TerminalKey.SHIFT_LEFT, mods = setOf(TerminalInputModifier.SHIFT))
    k.session.write(csi(">11u")) // disambiguate + report events + report all
    // The modifier counts as held on its own press and as up on its release, as AWT reports it.
    k.assertEncodes(csi("57441;2u"), TerminalKey.SHIFT_LEFT, mods = setOf(TerminalInputModifier.SHIFT))
    k.assertEncodes(csi("57441;1:3u"), TerminalKey.SHIFT_LEFT, action = TerminalKeyAction.RELEASE)
    k.assertEncodes(csi("57442;5:3u"), TerminalKey.CONTROL_LEFT, action = TerminalKeyAction.RELEASE, mods = setOf(TerminalInputModifier.CTRL))
  }

  // ---- Ghostty reference cases ----
  //
  // Each expected string below is the one the matching test of ghostty's src/input/key_encode.zig
  // asserts, so the two encoders cannot drift apart silently.
  // Cases whose text would be a control byte are left out: this layer never passes one.
  // A case whose result depends on the OS says so; the encoder decides some rules at compile time.

  @Test
  fun `ghostty kitty carve-outs keep enter, tab and backspace legacy until report-all`() = keys { k ->
    val shift = setOf(TerminalInputModifier.SHIFT)
    val alt = setOf(TerminalInputModifier.ALT)
    k.session.write(csi(">1u"))
    k.assertEncodes("\r", TerminalKey.ENTER)
    k.assertEncodes("\u007f", TerminalKey.BACKSPACE)
    k.assertEncodes("\t", TerminalKey.TAB)
    k.assertEncodes(csi("13;2u"), TerminalKey.ENTER, mods = shift)
    k.assertEncodes(csi("9;2u"), TerminalKey.TAB, mods = shift)
    k.assertEncodes(csi("127;2u"), TerminalKey.BACKSPACE, mods = shift)
    k.assertEncodes(csi("127;3u"), TerminalKey.BACKSPACE, mods = alt, consumed = alt)
    k.session.write(csi("?67h")) // DECBKM changes nothing under the protocol
    k.assertEncodes("\u007f", TerminalKey.BACKSPACE)
    k.session.write(csi(">3u")) // + report events: still no release for the three
    k.assertEncodes("", TerminalKey.ENTER, action = TerminalKeyAction.RELEASE)
    k.assertEncodes("", TerminalKey.BACKSPACE, action = TerminalKeyAction.RELEASE)
    k.assertEncodes("", TerminalKey.TAB, action = TerminalKeyAction.RELEASE)
    k.session.write(csi(">11u")) // + report all
    k.assertEncodes(csi("13;1:3u"), TerminalKey.ENTER, action = TerminalKeyAction.RELEASE)
    k.assertEncodes(csi("127;1:3u"), TerminalKey.BACKSPACE, action = TerminalKeyAction.RELEASE)
    k.assertEncodes(csi("9;1:3u"), TerminalKey.TAB, action = TerminalKeyAction.RELEASE)
    k.session.write(csi(">31u"))
    k.assertEncodes(csi("13u"), TerminalKey.ENTER)
    k.assertEncodes(csi("127u"), TerminalKey.BACKSPACE)
    k.assertEncodes(csi("57442;5u"), TerminalKey.CONTROL_LEFT, mods = setOf(TerminalInputModifier.CTRL))
  }

  @Test
  fun `ghostty kitty plain text, repeats, composing and text fallbacks`() = keys { k ->
    val shift = setOf(TerminalInputModifier.SHIFT)
    k.session.write(csi(">1u"))
    k.assertEncodes("abcd", TerminalKey.A, text = "abcd")
    k.assertEncodes("", TerminalKey.A, mods = shift, composing = true)
    k.session.write(csi(">9u")) // + report all
    k.assertEncodes(csi("57441;2u"), TerminalKey.SHIFT_LEFT, mods = shift, composing = true)
    k.assertEncodes(csi("57441u"), TerminalKey.SHIFT_LEFT)
    k.session.write(csi(">31u"))
    k.assertEncodes("û", TerminalKey.UNIDENTIFIED, text = "û") // composed text with report all
    k.assertEncodes("", TerminalKey.UNIDENTIFIED, action = TerminalKeyAction.RELEASE, mods = shift, text = "!")
    k.session.write(csi(">23u")) // all but report all
    k.assertEncodes("!", TerminalKey.UNIDENTIFIED, action = TerminalKeyAction.REPEAT, mods = shift, text = "!")
  }

  @Test
  fun `ghostty kitty report-alternates and report-associated follow the layout`() = keys { k ->
    val shift = setOf(TerminalInputModifier.SHIFT)
    val ctrl = setOf(TerminalInputModifier.CTRL)
    val alt = setOf(TerminalInputModifier.ALT)
    val caps = setOf(TerminalInputModifier.CAPS_LOCK)
    k.session.write(csi(">5u")) // disambiguate + report alternates
    k.assertEncodes(csi("97:65;2u"), TerminalKey.A, mods = shift, text = "A", unshifted = 'a'.code) // shift not consumed
    k.assertEncodes(csi("65::97;2u"), TerminalKey.A, mods = shift, text = "A", unshifted = 65)
    k.assertEncodes(csi("9;2u"), TerminalKey.TAB, mods = shift)
    k.assertEncodes("", TerminalKey.SHIFT_LEFT)
    k.session.write(csi(">29u")) // + report all + report associated text
    k.assertEncodes(csi("106;65;74u"), TerminalKey.J, mods = caps, text = "J", unshifted = 106)
    k.assertEncodes(csi("59:58;2;58u"), TerminalKey.SEMICOLON, mods = shift, text = ":", unshifted = ';'.code)
    k.assertEncodes(csi("1095::59;;1095u"), TerminalKey.SEMICOLON, text = "ч", unshifted = 1095) // ru layout
    k.assertEncodes(csi("1095:1063:59;2;1063u"), TerminalKey.SEMICOLON, mods = shift, text = "Ч", unshifted = 1095)
    k.assertEncodes(csi("1095::59;65;1063u"), TerminalKey.SEMICOLON, mods = caps, text = "Ч", unshifted = 1095)
    k.assertEncodes(csi("106;5u"), TerminalKey.J, mods = ctrl, text = "j", unshifted = 106) // ctrl prevents text
    k.assertEncodes(csi("106:74;2;74u"), TerminalKey.J, mods = shift, text = "J", unshifted = 106)
    k.session.emulator.setOptionAsAlt(true)
    k.assertEncodes(csi("119;3u"), TerminalKey.W, mods = alt, text = "∑", unshifted = 119) // alt prevents text
    k.assertEncodes(csi("119;;8721u"), TerminalKey.W, text = "∑", unshifted = 119)
    k.session.emulator.setOptionAsAlt(false) // on macOS Option composes text, so the text is reported
    k.assertEncodes(if (isMac) csi("119;3;8721u") else csi("119;3u"), TerminalKey.W, mods = alt, text = "∑", unshifted = 119)
    k.session.write(csi(">31u"))
    k.assertEncodes(csi("106:74;2:3u"), TerminalKey.J, action = TerminalKeyAction.RELEASE, mods = shift, text = "J", unshifted = 106)
    k.assertEncodes(csi("337::91;5:3u"), TerminalKey.BRACKET_LEFT, action = TerminalKeyAction.RELEASE, mods = ctrl, unshifted = 337) // hu layout
    k.assertEncodes(csi("57400;;49u"), TerminalKey.NUMPAD_1, text = "1")
  }

  @Test
  fun `ghostty legacy alt chords with Option as Alt`() = keys { k ->
    val alt = setOf(TerminalInputModifier.ALT)
    k.session.emulator.setOptionAsAlt(true)
    k.assertEncodes(esc("c"), TerminalKey.C, mods = alt, text = "c")
    k.assertEncodes(esc("e"), TerminalKey.E, mods = alt, unshifted = 'e'.code) // no text, only the unshifted codepoint
    k.assertEncodes(esc("界"), TerminalKey.UNIDENTIFIED, mods = alt, unshifted = '界'.code)
    k.assertEncodes(esc("😀"), TerminalKey.UNIDENTIFIED, mods = alt, text = "😀")
    k.assertEncodes(esc("ф"), TerminalKey.F, mods = alt, text = "ф")
    // On macOS the Option translation is undone through the unshifted codepoint; elsewhere the text stands.
    k.assertEncodes(if (isMac) esc("c") else esc("≈"), TerminalKey.C, mods = alt, text = "≈", unshifted = 'c'.code)
    k.assertEncodes(ESC_STR + ESC_STR, TerminalKey.ESCAPE, mods = alt)
    k.session.write(csi(">4;2m")) // modifyOtherKeys state 2
    k.assertEncodes(csi("27;3;27~"), TerminalKey.ESCAPE, mods = alt)
  }

  @Test
  fun `ghostty legacy ctrl chords`() = keys { k ->
    val ctrl = setOf(TerminalInputModifier.CTRL)
    val ctrlShift = setOf(TerminalInputModifier.CTRL, TerminalInputModifier.SHIFT)
    val ctrlAlt = setOf(TerminalInputModifier.CTRL, TerminalInputModifier.ALT)
    val ctrlCaps = setOf(TerminalInputModifier.CTRL, TerminalInputModifier.CAPS_LOCK)
    k.assertEncodes("\u001f", TerminalKey.MINUS, mods = ctrlShift, text = "_") // underscore on US
    k.assertEncodes(esc("\u0003"), TerminalKey.C, mods = ctrlAlt, text = "c")
    k.assertEncodes("\u0003", TerminalKey.C, mods = ctrlCaps, text = "C", unshifted = 'c'.code)
    k.assertEncodes("\u0003", TerminalKey.C, mods = ctrl, text = "с") // Cyrillic с: the logical key decides
    k.assertEncodes(esc("\u0003"), TerminalKey.C, mods = ctrlAlt, text = "с")
    k.assertEncodes("\u0008", TerminalKey.BACKSPACE, mods = ctrlShift)
    k.assertEncodes("\u0010", TerminalKey.P, mods = ctrl, text = "p")
    k.assertEncodes(csi("337;5u"), TerminalKey.BRACKET_LEFT, mods = ctrl, text = "ő", unshifted = 337) // hu layout
  }

  @Test
  fun `ghostty legacy modifyOtherKeys state 2`() = keys { k ->
    val ctrl = setOf(TerminalInputModifier.CTRL)
    val shift = setOf(TerminalInputModifier.SHIFT)
    val alt = setOf(TerminalInputModifier.ALT)
    k.session.write(csi(">4;2m"))
    k.assertEncodes(csi("27;6;72~"), TerminalKey.H, mods = ctrl + shift, text = "H")
    k.assertEncodes(csi("27;6;72~"), TerminalKey.H, mods = ctrl + shift, consumed = shift, text = "H")
    k.assertEncodes(csi("27;5;112~"), TerminalKey.P, mods = ctrl, text = "p")
    k.session.emulator.setOptionAsAlt(true)
    k.assertEncodes(csi("27;3;56~"), TerminalKey.DIGIT_8, mods = alt, text = "8")
    if (isMac) {
      k.session.emulator.setOptionAsAlt(false) // Option+8 types "[" on a European layout, and that is what goes out
      k.assertEncodes("[", TerminalKey.DIGIT_8, mods = alt, consumed = alt, text = "[")
    }
  }

  @Test
  fun `ghostty legacy DECBKM`() = keys { k ->
    val ctrl = setOf(TerminalInputModifier.CTRL)
    k.assertEncodes("\u007f", TerminalKey.BACKSPACE)
    k.assertEncodes("\u0008", TerminalKey.BACKSPACE, mods = ctrl)
    k.session.write(csi("?67h"))
    k.assertEncodes("\u0008", TerminalKey.BACKSPACE)
    k.assertEncodes("\u007f", TerminalKey.BACKSPACE, mods = ctrl)
  }

  @Test
  fun `ghostty legacy keypad`() = keys { k ->
    val ctrl = setOf(TerminalInputModifier.CTRL)
    val numLock = setOf(TerminalInputModifier.NUM_LOCK)
    k.assertEncodes("\r", TerminalKey.NUMPAD_ENTER)
    k.assertEncodes("1", TerminalKey.NUMPAD_1, text = "1")
    k.session.write(csi(">4;2m")) // modifyOtherKeys does not touch the keypad
    k.assertEncodes("1", TerminalKey.NUMPAD_1, mods = ctrl, text = "1")
    k.session.write(csi("?66h")) // application keypad, which mode 1035 (on by default) ignores
    k.assertEncodes("1", TerminalKey.NUMPAD_1, text = "1")
    k.session.write(csi("?1035l"))
    k.assertEncodes(esc("Oq"), TerminalKey.NUMPAD_1, text = "1")
    k.assertEncodes(esc("Oq"), TerminalKey.NUMPAD_1, mods = numLock, text = "1")
    k.assertEncodes(esc("O5q"), TerminalKey.NUMPAD_1, mods = ctrl, text = "1")
  }

  @Test
  fun `ghostty legacy function keys beyond F12, help and context menu`() = keys { k ->
    val codes = mapOf(
      TerminalKey.F13 to 25, TerminalKey.F14 to 26, TerminalKey.F15 to 28, TerminalKey.F16 to 29, TerminalKey.F17 to 31,
      TerminalKey.F18 to 32, TerminalKey.F19 to 33, TerminalKey.F20 to 34, TerminalKey.F21 to 42, TerminalKey.F22 to 43,
      TerminalKey.F23 to 44, TerminalKey.F24 to 45, TerminalKey.F25 to 46, TerminalKey.HELP to 28, TerminalKey.CONTEXT_MENU to 29,
    )
    for ((key, code) in codes) {
      k.assertEncodes(csi("$code~"), key)
    }
    k.session.write(csi(">4;2m"))
    for ((key, code) in codes) {
      k.assertEncodes(csi("$code;5~"), key, mods = setOf(TerminalInputModifier.CTRL))
    }
  }

  @Test
  fun `ghostty legacy shift+tab, consumed shift on a function key, and super on macOS`() = keys { k ->
    val shift = setOf(TerminalInputModifier.SHIFT)
    val ctrl = setOf(TerminalInputModifier.CTRL)
    val superKey = setOf(TerminalInputModifier.SUPER)
    k.assertEncodes(csi("Z"), TerminalKey.TAB, mods = shift)
    k.assertEncodes(csi("1;2A"), TerminalKey.ARROW_UP, mods = shift, consumed = shift) // PC-style keys use all mods
    k.assertEncodes(csi("1;5Q"), TerminalKey.F2, mods = ctrl)
    k.assertEncodes(csi("13;5~"), TerminalKey.F3, mods = ctrl)
    k.assertEncodes(csi("1;5S"), TerminalKey.F4, mods = ctrl)
    // Command chords never type text on macOS; on Linux Super+b types "b", as in GNOME Console.
    k.assertEncodes(if (isMac) "" else "b", TerminalKey.B, mods = superKey, text = "b")
    k.assertEncodes(if (isMac) "" else "B", TerminalKey.B, mods = superKey + shift, text = "B")
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
      consumed: Set<TerminalInputModifier> = emptySet(),
      text: String = "",
      unshifted: Int = 0,
      awtKey: Int? = null,
      awtMods: Int = 0,
      composing: Boolean = false,
    ) {
      val event = TerminalKeyEvent(key, action, mods, text, unshifted, composing, consumed)
      // UTF-8, not Latin-1: the encoder writes escape sequences and UTF-8 text, never a lone high byte.
      val actual = session.emulator.encodeKeyEvent(event).toString(Charsets.UTF_8)
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

/** The encoder decides a few rules at compile time for macOS; the reference cases say which. */
private val isMac: Boolean = System.getProperty("os.name").startsWith("Mac")

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
