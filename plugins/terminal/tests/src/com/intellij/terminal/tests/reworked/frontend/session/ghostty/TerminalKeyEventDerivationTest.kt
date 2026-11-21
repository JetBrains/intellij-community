// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.ghostty

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.emulator.KittyKeyboardFlag
import com.intellij.terminal.emulator.TerminalEmulator
import com.intellij.terminal.emulator.TerminalInputModifier.ALT
import com.intellij.terminal.emulator.TerminalInputModifier.CTRL
import com.intellij.terminal.emulator.TerminalInputModifier.SHIFT
import com.intellij.terminal.emulator.TerminalKey
import com.intellij.terminal.emulator.TerminalKeyAction
import com.intellij.terminal.emulator.TerminalKeyEvent
import com.intellij.terminal.frontend.session.ghostty.TerminalEmulatorKeyEventEncoder
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.session.impl.dto.KeyEventProcessingResultDto
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.lang.reflect.Proxy
import javax.swing.JPanel

/**
 * The [TerminalKeyEvent] that [TerminalEmulatorKeyEventEncoder] derives from an AWT key event,
 * field by field. `KeyEncodingTest` pins what the emulator does with a given event; this class
 * pins that the event itself is right. A wrong field hides behind correct-looking bytes: the
 * emulator encoded the Shift+2 of IJPL-255707 correctly, into the wrong sequence.
 *
 * Every expected event is written out by hand, never derived from the encoder.
 */
@TestApplication
internal class TerminalKeyEventDerivationTest {
  private val source = JPanel()
  private val emulator = RecordingEmulator()
  private val events get() = emulator.events

  /** The "Alt sends Escape" setting; on here by default, as it is on Windows and Linux. */
  private var altSendsEscape = true
  private val settings = object : JBTerminalSystemSettingsProviderBase() {
    override fun altSendsEscape(): Boolean = altSendsEscape
  }
  private val encoder = TerminalEmulatorKeyEventEncoder(emulator.proxy, settings, isWinConPty = false, lockingKeys = { emptySet() })

  @Test
  fun `a typed character carries the physical key of its pressed half`() {
    press(KeyEvent.VK_A, 'a')
    type('a')
    release(KeyEvent.VK_A, 'a')
    press(KeyEvent.VK_A, 'A', SHIFT_MASK)
    type('A', SHIFT_MASK)
    release(KeyEvent.VK_A, 'A', SHIFT_MASK)
    press(KeyEvent.VK_2, '@', SHIFT_MASK)
    type('@', SHIFT_MASK)
    assertThat(events).containsExactly(
      TerminalKeyEvent(TerminalKey.A, text = "a", unshiftedCodepoint = 'a'.code),
      TerminalKeyEvent(TerminalKey.A, modifiers = setOf(SHIFT), text = "A", unshiftedCodepoint = 'a'.code, consumedModifiers = setOf(SHIFT)),
      TerminalKeyEvent(TerminalKey.DIGIT_2, modifiers = setOf(SHIFT), text = "@", unshiftedCodepoint = '2'.code, consumedModifiers = setOf(SHIFT)),
    )
  }

  @Test
  fun `every writing-system punctuation key names its TerminalKey and its US code points`() {
    val keys = listOf(
      Triple(KeyEvent.VK_BACK_QUOTE, TerminalKey.BACKQUOTE, "`~"),
      Triple(KeyEvent.VK_MINUS, TerminalKey.MINUS, "-_"),
      Triple(KeyEvent.VK_EQUALS, TerminalKey.EQUAL, "=+"),
      Triple(KeyEvent.VK_OPEN_BRACKET, TerminalKey.BRACKET_LEFT, "[{"),
      Triple(KeyEvent.VK_CLOSE_BRACKET, TerminalKey.BRACKET_RIGHT, "]}"),
      Triple(KeyEvent.VK_BACK_SLASH, TerminalKey.BACKSLASH, "\\|"),
      Triple(KeyEvent.VK_SEMICOLON, TerminalKey.SEMICOLON, ";:"),
      Triple(KeyEvent.VK_QUOTE, TerminalKey.QUOTE, "'\""),
      Triple(KeyEvent.VK_COMMA, TerminalKey.COMMA, ",<"),
      Triple(KeyEvent.VK_PERIOD, TerminalKey.PERIOD, ".>"),
      Triple(KeyEvent.VK_SLASH, TerminalKey.SLASH, "/?"),
    )
    for ((keyCode, key, chars) in keys) {
      val plain = chars[0]
      val shifted = chars[1]
      events.clear()
      press(keyCode, plain)
      type(plain)
      release(keyCode, plain)
      press(keyCode, shifted, SHIFT_MASK)
      type(shifted, SHIFT_MASK)
      assertThat(events).describedAs(key.name).containsExactly(
        TerminalKeyEvent(key, text = plain.toString(), unshiftedCodepoint = plain.code),
        TerminalKeyEvent(key, modifiers = setOf(SHIFT), text = shifted.toString(), unshiftedCodepoint = plain.code, consumedModifiers = setOf(SHIFT)),
      )
    }
  }

  @Test
  fun `a functional key carries no text, and F13 and above are not mapped yet`() {
    press(KeyEvent.VK_TAB, '\t')
    type('\t')
    press(KeyEvent.VK_ESCAPE, Char(27))
    type(Char(27))
    press(KeyEvent.VK_BACK_SPACE, '\b')
    type('\b')
    press(KeyEvent.VK_UP)
    press(KeyEvent.VK_HOME)
    press(KeyEvent.VK_F5)
    assertThat(events).containsExactly(
      TerminalKeyEvent(TerminalKey.TAB),
      TerminalKeyEvent(TerminalKey.ESCAPE),
      TerminalKeyEvent(TerminalKey.BACKSPACE),
      TerminalKeyEvent(TerminalKey.ARROW_UP),
      TerminalKeyEvent(TerminalKey.HOME),
      TerminalKeyEvent(TerminalKey.F5),
    )
    events.clear()
    assertThat(press(KeyEvent.VK_F13)).isEqualTo(KeyEventProcessingResultDto.Unhandled) // VK_F13 starts a separate block
    assertThat(events).isEmpty()
  }

  @Test
  fun `a typed character on another layout keeps its own unshifted codepoint`() {
    // The Russian layout types ф on the A key: the physical key is known, the layout is not. Its
    // Shift+2 types a quote, and the digit stays the unshifted code point of a digit key.
    press(KeyEvent.VK_A, 'Ф', SHIFT_MASK)
    type('Ф', SHIFT_MASK)
    press(KeyEvent.VK_2, '"', SHIFT_MASK)
    type('"', SHIFT_MASK)
    assertThat(events).containsExactly(
      TerminalKeyEvent(TerminalKey.A, modifiers = setOf(SHIFT), text = "Ф", unshiftedCodepoint = 'ф'.code, consumedModifiers = setOf(SHIFT)),
      TerminalKeyEvent(TerminalKey.DIGIT_2, modifiers = setOf(SHIFT), text = "\"", unshiftedCodepoint = '2'.code, consumedModifiers = setOf(SHIFT)),
    )
  }

  @Test
  fun `a space keyChar names the text only on the Space key`() {
    // macOS reports Ctrl+Shift+2, which is Ctrl+@, with a space, the same as Ctrl+Space (probe of 2026-10-01).
    press(KeyEvent.VK_SPACE, ' ', CTRL_MASK)
    press(KeyEvent.VK_2, ' ', CTRL_MASK or SHIFT_MASK)
    assertThat(events).containsExactly(
      TerminalKeyEvent(TerminalKey.SPACE, modifiers = setOf(CTRL), text = " ", unshiftedCodepoint = ' '.code),
      TerminalKeyEvent(TerminalKey.DIGIT_2, modifiers = setOf(CTRL, SHIFT), text = "@", unshiftedCodepoint = '2'.code),
    )
  }

  @Test
  fun `a typed character without a pressed half names no key`() {
    type('x')
    assertThat(events).containsExactly(
      TerminalKeyEvent(TerminalKey.UNIDENTIFIED, text = "x", unshiftedCodepoint = 'x'.code),
    )
  }

  @Test
  fun `shift stays a modifier of a typed character it did not change`() {
    // Shift types the same space on the Space key, so Shift+Space is a chord a program can bind
    // under the Kitty keyboard protocol. A key outside the US table counts as changed: its symbol
    // is all there is.
    press(KeyEvent.VK_SPACE, ' ', SHIFT_MASK)
    type(' ', SHIFT_MASK)
    type('Ö', SHIFT_MASK)
    assertThat(events).containsExactly(
      TerminalKeyEvent(TerminalKey.SPACE, modifiers = setOf(SHIFT), text = " ", unshiftedCodepoint = ' '.code),
      TerminalKeyEvent(TerminalKey.UNIDENTIFIED, modifiers = setOf(SHIFT), text = "Ö", unshiftedCodepoint = 'ö'.code, consumedModifiers = setOf(SHIFT)),
    )
  }

  @Test
  fun `a press without a typed half lends nothing to a later typed character`() {
    // A bare Shift has no typed half, and the encoder produces nothing for it outside the Kitty
    // protocol. Its release ends the keystroke. The typed half of Ctrl+Shift+2 is a control
    // character, which ends the keystroke too. A typed character that arrives on its own
    // afterwards must not inherit the key or the repeat state of either press.
    press(KeyEvent.VK_SHIFT, modifiers = SHIFT_MASK, location = KeyEvent.KEY_LOCATION_LEFT)
    hold(KeyEvent.VK_SHIFT, modifiers = SHIFT_MASK, location = KeyEvent.KEY_LOCATION_LEFT)
    release(KeyEvent.VK_SHIFT, location = KeyEvent.KEY_LOCATION_LEFT)
    type('x')
    press(KeyEvent.VK_2, ' ', CTRL_MASK or SHIFT_MASK)
    type(Char(0), CTRL_MASK or SHIFT_MASK)
    type('y')
    assertThat(events).containsExactly(
      TerminalKeyEvent(TerminalKey.SHIFT_LEFT, modifiers = setOf(SHIFT)),
      TerminalKeyEvent(TerminalKey.SHIFT_LEFT, TerminalKeyAction.REPEAT, modifiers = setOf(SHIFT)),
      TerminalKeyEvent(TerminalKey.UNIDENTIFIED, text = "x", unshiftedCodepoint = 'x'.code),
      TerminalKeyEvent(TerminalKey.DIGIT_2, modifiers = setOf(CTRL, SHIFT), text = "@", unshiftedCodepoint = '2'.code),
      TerminalKeyEvent(TerminalKey.UNIDENTIFIED, text = "y", unshiftedCodepoint = 'y'.code),
    )
  }

  @Test
  fun `a handled press lends nothing to a later typed character`() {
    // The handler swallows the typed half of a handled press. A typed character that arrives on its
    // own afterwards (a robot stream, a commit) must not inherit that press's key or repeat state.
    emulator.encodesEverything = true
    press(KeyEvent.VK_C, Char(3), CTRL_MASK)
    hold(KeyEvent.VK_C, Char(3), CTRL_MASK)
    type('x')
    assertThat(events).containsExactly(
      TerminalKeyEvent(TerminalKey.C, modifiers = setOf(CTRL), text = "c", unshiftedCodepoint = 'c'.code),
      TerminalKeyEvent(TerminalKey.C, TerminalKeyAction.REPEAT, modifiers = setOf(CTRL), text = "c", unshiftedCodepoint = 'c'.code),
      TerminalKeyEvent(TerminalKey.UNIDENTIFIED, text = "x", unshiftedCodepoint = 'x'.code),
    )
  }

  @Test
  fun `a ctrl chord carries the character without ctrl`() {
    press(KeyEvent.VK_C, Char(3), CTRL_MASK)
    press(KeyEvent.VK_M, Char(13), CTRL_MASK or SHIFT_MASK)
    press(KeyEvent.VK_SEMICOLON, ';', CTRL_MASK)
    press(KeyEvent.VK_SPACE, ' ', CTRL_MASK or SHIFT_MASK)
    assertThat(events).containsExactly(
      TerminalKeyEvent(TerminalKey.C, modifiers = setOf(CTRL), text = "c", unshiftedCodepoint = 'c'.code),
      TerminalKeyEvent(TerminalKey.M, modifiers = setOf(CTRL, SHIFT), text = "M", unshiftedCodepoint = 'm'.code),
      TerminalKeyEvent(TerminalKey.SEMICOLON, modifiers = setOf(CTRL), text = ";", unshiftedCodepoint = ';'.code),
      TerminalKeyEvent(TerminalKey.SPACE, modifiers = setOf(CTRL, SHIFT), text = " ", unshiftedCodepoint = ' '.code),
    )
  }

  @Test
  fun `ctrl+i, ctrl+m and ctrl+bracket keep their classic bytes outside the Kitty protocol`() {
    // The encoder never sees them there, because it would apply fixterms. An Alt chord gets the ESC prefix.
    val results = listOf(
      press(KeyEvent.VK_I, Char(9), CTRL_MASK),
      press(KeyEvent.VK_M, Char(13), CTRL_MASK),
      press(KeyEvent.VK_OPEN_BRACKET, Char(27), CTRL_MASK),
      press(KeyEvent.VK_M, Char(13), CTRL_MASK or ALT_MASK),
    )
    assertThat(events).isEmpty()
    assertThat(results.map { (it as KeyEventProcessingResultDto.BytesResult).bytes.map(Byte::toInt) })
      .containsExactly(listOf(9), listOf(13), listOf(27), listOf(27, 13))

    // Under a flag the encoder gets the chord like any other, with its text.
    release(KeyEvent.VK_I, Char(9), CTRL_MASK)
    emulator.kittyKeyboardFlags = setOf(KittyKeyboardFlag.DISAMBIGUATE_ESCAPE_CODES)
    press(KeyEvent.VK_I, Char(9), CTRL_MASK)
    assertThat(events).containsExactly(
      TerminalKeyEvent(TerminalKey.I, modifiers = setOf(CTRL), text = "i", unshiftedCodepoint = 'i'.code),
    )
  }

  /**
   * TODO: Merge this case into `encoding Ctrl+Enter without a ConPTY` of [TerminalSessionKeyEventTest],
   *  when [GhosttyTerminalSessionTestCase.runSessionTest] can create a session with a custom `isWinConPty` flag.
   */
  @Test
  fun `ctrl+enter is LF only for a ConPTY outside the Kitty protocol`(@TestDisposable disposable: Disposable) {
    setCtrlEnterSendsLf(true, disposable)
    fun enter(id: Int, modifiers: Int) = KeyEvent(source, id, 0, modifiers, KeyEvent.VK_ENTER, '\n')
    val conPtyEncoder = TerminalEmulatorKeyEventEncoder(emulator.proxy, settings, isWinConPty = true, lockingKeys = { emptySet() })

    // The emulator never sees the chord there.
    val result = conPtyEncoder.encodeKeyEvent(enter(KeyEvent.KEY_PRESSED, CTRL_MASK))
    assertThat((result as KeyEventProcessingResultDto.BytesResult).bytes.map(Byte::toInt)).containsExactly(10)
    conPtyEncoder.encodeKeyEvent(enter(KeyEvent.KEY_RELEASED, CTRL_MASK))
    assertThat(events).isEmpty()

    // In each other case the emulator gets the chord.
    fun assertEmulatorGetsChord(case: String, encoder: TerminalEmulatorKeyEventEncoder, modifiers: Int, expected: TerminalKeyEvent) {
      events.clear()
      encoder.encodeKeyEvent(enter(KeyEvent.KEY_PRESSED, modifiers))
      encoder.encodeKeyEvent(enter(KeyEvent.KEY_RELEASED, modifiers))
      assertThat(events).describedAs(case).containsExactly(expected)
    }

    val ctrlEnter = TerminalKeyEvent(TerminalKey.ENTER, modifiers = setOf(CTRL))
    val ctrlShiftEnter = TerminalKeyEvent(TerminalKey.ENTER, modifiers = setOf(CTRL, SHIFT))
    assertEmulatorGetsChord("with Shift", conPtyEncoder, CTRL_MASK or SHIFT_MASK, ctrlShiftEnter)
    assertEmulatorGetsChord("without a ConPTY", encoder, CTRL_MASK, ctrlEnter)
    setCtrlEnterSendsLf(false, disposable)
    assertEmulatorGetsChord("with the setting off", conPtyEncoder, CTRL_MASK, ctrlEnter)
    setCtrlEnterSendsLf(true, disposable)
    emulator.kittyKeyboardFlags = setOf(KittyKeyboardFlag.DISAMBIGUATE_ESCAPE_CODES)
    assertEmulatorGetsChord("under the Kitty protocol", conPtyEncoder, CTRL_MASK, ctrlEnter)
  }

  @Test
  fun `the Menu key stays with the IDE`() {
    emulator.encodesEverything = true
    assertThat(press(KeyEvent.VK_CONTEXT_MENU)).isEqualTo(KeyEventProcessingResultDto.Unhandled)
    assertThat(events).isEmpty()
  }

  @Test
  fun `a control character is never the text`() {
    press(KeyEvent.VK_ENTER, Char(10))
    type(Char(10))
    assertThat(events).containsExactly(TerminalKeyEvent(TerminalKey.ENTER))
  }

  @Test
  fun `an alt chord carries the key's US character and the Alt modifier when Alt sends Escape`() {
    // macOS types ƒ for Option+F; the shell wants f, and the encoder adds the ESC prefix.
    press(KeyEvent.VK_F, 'ƒ', ALT_MASK)
    release(KeyEvent.VK_F, 'ƒ', ALT_MASK)
    press(KeyEvent.VK_F, 'F', ALT_MASK or SHIFT_MASK)
    press(KeyEvent.VK_PERIOD, '>', ALT_MASK or SHIFT_MASK)
    assertThat(events).containsExactly(
      TerminalKeyEvent(TerminalKey.F, modifiers = setOf(ALT), text = "f", unshiftedCodepoint = 'f'.code),
      TerminalKeyEvent(TerminalKey.F, modifiers = setOf(ALT, SHIFT), text = "F", unshiftedCodepoint = 'f'.code),
      TerminalKeyEvent(TerminalKey.PERIOD, modifiers = setOf(ALT, SHIFT), text = ">", unshiftedCodepoint = '.'.code),
    )
  }

  @Test
  fun `an alt chord on a key outside the US table carries the character AWT computed`() {
    // Alt+Ö on a German layout: AWT names no key, so the character is all there is.
    press(KeyEvent.VK_UNDEFINED, 'ö', ALT_MASK)
    assertThat(events).containsExactly(
      TerminalKeyEvent(TerminalKey.UNIDENTIFIED, modifiers = setOf(ALT), text = "ö", unshiftedCodepoint = 'ö'.code),
    )
  }

  @Test
  fun `an alt chord types its composed character when Alt does not send Escape`() {
    altSendsEscape = false
    press(KeyEvent.VK_F, 'ƒ', ALT_MASK)
    type('ƒ', ALT_MASK)
    assertThat(events).containsExactly(
      TerminalKeyEvent(TerminalKey.F, text = "ƒ", unshiftedCodepoint = 'ƒ'.code),
    )
  }

  @Test
  fun `a bare modifier key names its side`() {
    press(KeyEvent.VK_SHIFT, modifiers = SHIFT_MASK, location = KeyEvent.KEY_LOCATION_LEFT)
    press(KeyEvent.VK_SHIFT, modifiers = SHIFT_MASK, location = KeyEvent.KEY_LOCATION_RIGHT)
    press(KeyEvent.VK_CONTROL, modifiers = CTRL_MASK, location = KeyEvent.KEY_LOCATION_LEFT)
    assertThat(events).containsExactly(
      TerminalKeyEvent(TerminalKey.SHIFT_LEFT, modifiers = setOf(SHIFT)),
      TerminalKeyEvent(TerminalKey.SHIFT_RIGHT, modifiers = setOf(SHIFT)),
      TerminalKeyEvent(TerminalKey.CONTROL_LEFT, modifiers = setOf(CTRL)),
    )
  }

  // ---- releases and repeats: the program sees them only under the Kitty protocol ----

  @Test
  fun `a release repeats the key, the code point and the text of its press`() {
    emulator.reportEvents()
    press(KeyEvent.VK_A, 'A', SHIFT_MASK)
    type('A', SHIFT_MASK)
    release(KeyEvent.VK_A, 'A', SHIFT_MASK)
    press(KeyEvent.VK_LEFT)
    release(KeyEvent.VK_LEFT)
    press(KeyEvent.VK_SHIFT, modifiers = SHIFT_MASK, location = KeyEvent.KEY_LOCATION_LEFT)
    release(KeyEvent.VK_SHIFT, location = KeyEvent.KEY_LOCATION_LEFT)
    // Option+F: the press reports the US character f, and the release must name the same key.
    press(KeyEvent.VK_F, 'ƒ', ALT_MASK)
    release(KeyEvent.VK_F, 'ƒ', ALT_MASK)
    assertThat(events).containsExactly(
      TerminalKeyEvent(TerminalKey.A, modifiers = setOf(SHIFT), text = "A", unshiftedCodepoint = 'a'.code, consumedModifiers = setOf(SHIFT)),
      TerminalKeyEvent(TerminalKey.A, TerminalKeyAction.RELEASE, modifiers = setOf(SHIFT), text = "A", unshiftedCodepoint = 'a'.code, consumedModifiers = setOf(SHIFT)),
      TerminalKeyEvent(TerminalKey.ARROW_LEFT),
      TerminalKeyEvent(TerminalKey.ARROW_LEFT, TerminalKeyAction.RELEASE),
      TerminalKeyEvent(TerminalKey.SHIFT_LEFT, modifiers = setOf(SHIFT)),
      TerminalKeyEvent(TerminalKey.SHIFT_LEFT, TerminalKeyAction.RELEASE),
      TerminalKeyEvent(TerminalKey.F, modifiers = setOf(ALT), text = "f", unshiftedCodepoint = 'f'.code),
      TerminalKeyEvent(TerminalKey.F, TerminalKeyAction.RELEASE, modifiers = setOf(ALT), text = "f", unshiftedCodepoint = 'f'.code),
    )
  }

  @Test
  fun `a release is reported only for a key whose press the program saw`() {
    emulator.reportEvents()
    release(KeyEvent.VK_UP) // no press at all: the key went down before the terminal had the focus
    emulator.encodesEverything = false
    press(KeyEvent.VK_UP) // the encoder produced nothing for the press
    release(KeyEvent.VK_UP)
    events.clear()
    emulator.encodesEverything = true
    press(KeyEvent.VK_DOWN)
    release(KeyEvent.VK_DOWN)
    assertThat(events).containsExactly(
      TerminalKeyEvent(TerminalKey.ARROW_DOWN),
      TerminalKeyEvent(TerminalKey.ARROW_DOWN, TerminalKeyAction.RELEASE),
    )
  }

  @Test
  fun `a release is not even encoded outside the Kitty protocol`() {
    emulator.encodesEverything = true
    press(KeyEvent.VK_UP)
    events.clear()
    assertThat(release(KeyEvent.VK_UP)).isEqualTo(KeyEventProcessingResultDto.Unhandled)
    assertThat(events).isEmpty()
  }

  @Test
  fun `a release is not reported for a press that went out before the program enabled the protocol`() {
    // The shell got a legacy Enter. The program it started enabled the protocol before the key came up.
    emulator.encodesEverything = true
    press(KeyEvent.VK_ENTER, '\n')
    emulator.reportEvents()
    events.clear()
    assertThat(release(KeyEvent.VK_ENTER, '\n')).isEqualTo(KeyEventProcessingResultDto.Unhandled)
    assertThat(events).isEmpty()
  }

  @Test
  fun `the release of a typed character leaves Ctrl and Alt out, as its press did`() {
    emulator.reportEvents()
    // Windows reports AltGr as Ctrl+Alt down. The typed half drops them, and so must the release.
    val altGr = CTRL_MASK or ALT_MASK or InputEvent.ALT_GRAPH_DOWN_MASK
    press(KeyEvent.VK_Q, '@', altGr)
    type('@', altGr)
    release(KeyEvent.VK_Q, '@', altGr)
    // On macOS, Option composes text when it does not act as Alt.
    altSendsEscape = false
    press(KeyEvent.VK_A, 'å', ALT_MASK)
    type('å', ALT_MASK)
    release(KeyEvent.VK_A, 'å', ALT_MASK)
    assertThat(events).containsExactly(
      TerminalKeyEvent(TerminalKey.Q, text = "@", unshiftedCodepoint = '@'.code),
      TerminalKeyEvent(TerminalKey.Q, TerminalKeyAction.RELEASE, text = "@", unshiftedCodepoint = '@'.code),
      TerminalKeyEvent(TerminalKey.A, text = "å", unshiftedCodepoint = 'å'.code),
      TerminalKeyEvent(TerminalKey.A, TerminalKeyAction.RELEASE, text = "å", unshiftedCodepoint = 'å'.code),
    )
  }

  @Test
  fun `keys AWT reports as VK_UNDEFINED are held apart`() {
    // Ö and Ä on a German layout both come as VK_UNDEFINED. A rollover of the two must not make
    // the second press a repeat, nor swap their releases.
    emulator.reportEvents()
    undefinedKey(KeyEvent.KEY_PRESSED, 'ö')
    type('ö')
    undefinedKey(KeyEvent.KEY_PRESSED, 'ä')
    type('ä')
    undefinedKey(KeyEvent.KEY_RELEASED, 'ö')
    undefinedKey(KeyEvent.KEY_RELEASED, 'ä')
    assertThat(events).containsExactly(
      TerminalKeyEvent(TerminalKey.UNIDENTIFIED, text = "ö", unshiftedCodepoint = 'ö'.code),
      TerminalKeyEvent(TerminalKey.UNIDENTIFIED, text = "ä", unshiftedCodepoint = 'ä'.code),
      TerminalKeyEvent(TerminalKey.UNIDENTIFIED, TerminalKeyAction.RELEASE, text = "ö", unshiftedCodepoint = 'ö'.code),
      TerminalKeyEvent(TerminalKey.UNIDENTIFIED, TerminalKeyAction.RELEASE, text = "ä", unshiftedCodepoint = 'ä'.code),
    )
  }

  @Test
  fun `a second press of a held key is a repeat, and so is its typed character`() {
    emulator.reportEvents()
    press(KeyEvent.VK_A, 'a')
    type('a')
    hold(KeyEvent.VK_A, 'a')
    type('a')
    release(KeyEvent.VK_A, 'a')
    press(KeyEvent.VK_A, 'a')
    type('a')
    assertThat(events).containsExactly(
      TerminalKeyEvent(TerminalKey.A, text = "a", unshiftedCodepoint = 'a'.code),
      TerminalKeyEvent(TerminalKey.A, TerminalKeyAction.REPEAT, text = "a", unshiftedCodepoint = 'a'.code),
      TerminalKeyEvent(TerminalKey.A, TerminalKeyAction.RELEASE, text = "a", unshiftedCodepoint = 'a'.code),
      TerminalKeyEvent(TerminalKey.A, text = "a", unshiftedCodepoint = 'a'.code),
    )
  }

  @Test
  fun `a focus loss forgets the held keys`() {
    // The release went to the component that took the focus. The next press is a new press, and
    // the release the terminal sees later pairs with no press.
    emulator.reportEvents()
    press(KeyEvent.VK_A, 'a')
    type('a')
    encoder.focusLost()
    press(KeyEvent.VK_A, 'a')
    type('a')
    encoder.focusLost()
    assertThat(release(KeyEvent.VK_A, 'a')).isEqualTo(KeyEventProcessingResultDto.Unhandled)
    assertThat(events).containsExactly(
      TerminalKeyEvent(TerminalKey.A, text = "a", unshiftedCodepoint = 'a'.code),
      TerminalKeyEvent(TerminalKey.A, text = "a", unshiftedCodepoint = 'a'.code),
    )
  }

  private fun press(keyCode: Int, keyChar: Char = KeyEvent.CHAR_UNDEFINED, modifiers: Int = 0, location: Int = KeyEvent.KEY_LOCATION_STANDARD): KeyEventProcessingResultDto =
    encode(KeyEvent.KEY_PRESSED, keyCode, keyChar, modifiers, location)

  /** A press of a key that is still held down: autorepeat. */
  private fun hold(keyCode: Int, keyChar: Char = KeyEvent.CHAR_UNDEFINED, modifiers: Int = 0, location: Int = KeyEvent.KEY_LOCATION_STANDARD): KeyEventProcessingResultDto =
    encode(KeyEvent.KEY_PRESSED, keyCode, keyChar, modifiers, location)

  private fun release(keyCode: Int, keyChar: Char = KeyEvent.CHAR_UNDEFINED, modifiers: Int = 0, location: Int = KeyEvent.KEY_LOCATION_STANDARD): KeyEventProcessingResultDto =
    encode(KeyEvent.KEY_RELEASED, keyCode, keyChar, modifiers, location)

  private fun type(keyChar: Char, modifiers: Int = 0): KeyEventProcessingResultDto =
    encode(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, keyChar, modifiers, KeyEvent.KEY_LOCATION_UNKNOWN)

  private fun encode(id: Int, keyCode: Int, keyChar: Char, modifiers: Int, location: Int): KeyEventProcessingResultDto =
    encoder.encodeKeyEvent(KeyEvent(source, id, 0, modifiers, keyCode, keyChar, location))

  /** A key outside the AWT table: the key code is VK_UNDEFINED, and the extended key code names the character. */
  private fun undefinedKey(id: Int, keyChar: Char): KeyEventProcessingResultDto =
    encoder.encodeKeyEvent(object : KeyEvent(source, id, 0, 0, VK_UNDEFINED, keyChar, KEY_LOCATION_STANDARD) {
      override fun getExtendedKeyCode(): Int = getExtendedKeyCodeForChar(keyChar.code)
    })
}

/** Sets the "Send LF on Ctrl+Enter on Windows" setting until [disposable] is disposed. */
internal fun setCtrlEnterSendsLf(value: Boolean, disposable: Disposable) {
  val settingId = TerminalEmulatorKeyEventEncoder.CTRL_ENTER_SENDS_LF_SETTING_ID
  val previousValue = AdvancedSettings.getBoolean(settingId)
  AdvancedSettings.setBoolean(settingId, value)
  Disposer.register(disposable) { AdvancedSettings.setBoolean(settingId, previousValue) }
}

private const val SHIFT_MASK = InputEvent.SHIFT_DOWN_MASK
private const val CTRL_MASK = InputEvent.CTRL_DOWN_MASK
private const val ALT_MASK = InputEvent.ALT_DOWN_MASK

/**
 * A [TerminalEmulator] that records the key events it is asked to encode. By default it encodes
 * nothing, so the encoder takes its unhandled branches; with [encodesEverything] every event gets
 * one byte, so the encoder treats the keystroke as one the program saw. It also answers the two
 * other calls the encoder makes: the Kitty flags and the Option-as-Alt setting.
 */
private class RecordingEmulator {
  val events = mutableListOf<TerminalKeyEvent>()
  var encodesEverything = false
  var kittyKeyboardFlags: Set<KittyKeyboardFlag> = emptySet()

  /** The state in which a program asked for release and repeat events. */
  fun reportEvents() {
    encodesEverything = true
    kittyKeyboardFlags = setOf(KittyKeyboardFlag.DISAMBIGUATE_ESCAPE_CODES, KittyKeyboardFlag.REPORT_EVENT_TYPES)
  }

  val proxy: TerminalEmulator =
    Proxy.newProxyInstance(TerminalEmulator::class.java.classLoader, arrayOf(TerminalEmulator::class.java)) { _, method, args ->
      when (method.name) {
        "encodeKeyEvent" -> {
          events += args[0] as TerminalKeyEvent
          if (encodesEverything) byteArrayOf(0) else ByteArray(0)
        }
        "setOptionAsAlt" -> null
        "getKittyKeyboardFlags" -> kittyKeyboardFlags
        else -> throw UnsupportedOperationException(method.name)
      }
    } as TerminalEmulator
}
