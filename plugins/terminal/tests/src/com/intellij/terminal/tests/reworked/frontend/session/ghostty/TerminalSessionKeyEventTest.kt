// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.ghostty

import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.terminal.tests.reworked.util.LoopbackTtyConnector
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.session.impl.dto.KeyEventProcessingResultDto
import org.junit.Assume
import org.junit.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JPanel

/**
 * The Ghostty-backed session's
 * [processKeyEvent][org.jetbrains.plugins.terminal.session.impl.TerminalSession.processKeyEvent]:
 * AWT key events must be encoded into PTY bytes by the emulator's encoder (so terminal
 * modes are honored), while session-layer policy (macOS natural-editing chords,
 * Alt-as-Escape) is applied on top.
 *
 * The exact escape sequences per mode are pinned by the emulator module's
 * `KeyEncodingTest`; here the subject is the AWT-to-emulator translation and its policy.
 */
internal class TerminalSessionKeyEventTest : GhosttyTerminalSessionTestCase() {

  private val eventSource = JPanel()

  @Test
  fun `pressed keys are encoded by the emulator`() = runSessionTest { session, _, _ ->
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_ENTER, Char(10))))).isEqualTo(Char(13).toString())
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_BACK_SPACE, Char(8))))).isEqualTo(Char(127).toString())
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_UP)))).isEqualTo(csi("A"))
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_F5)))).isEqualTo(csi("15~"))
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_UP, modifiers = InputEvent.CTRL_DOWN_MASK))))
      .isEqualTo(csi("1;5A"))
  }

  @Test
  fun `encoding Shift+Enter when the Esc+CR setting is on`() {
    setShiftEnterSendsEscCr(true)
    runSessionTest { session, connector, _ ->
      fun shiftEnter() = pressed(KeyEvent.VK_ENTER, Char(10), InputEvent.SHIFT_DOWN_MASK)
      assertThat(bytesOf(session.processKeyEvent(shiftEnter()))).isEqualTo(Char(27) + "\r")
      applyModes(connector, csi(">4;2m")) // turns on modifyOtherKeys state 2
      assertThat(bytesOf(session.processKeyEvent(shiftEnter()))).isEqualTo(Char(27) + "\r")
      applyModes(connector, csi(">1u")) // pushes the Kitty flag 1, "disambiguate escape codes"
      assertThat(bytesOf(session.processKeyEvent(shiftEnter()))).isEqualTo(csi("13;2u"))
      // The setting changes only Shift+Enter without other modifiers.
      assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_ENTER, Char(10))))).isEqualTo("\r")
      val ctrlShiftEnter = pressed(KeyEvent.VK_ENTER, Char(10), InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)
      assertThat(bytesOf(session.processKeyEvent(ctrlShiftEnter))).isEqualTo(csi("13;6u"))
    }
  }

  @Test
  fun `encoding Shift+Enter when the Esc+CR setting is off`() {
    setShiftEnterSendsEscCr(false)
    runSessionTest { session, connector, _ ->
      fun shiftEnter() = pressed(KeyEvent.VK_ENTER, Char(10), InputEvent.SHIFT_DOWN_MASK)
      assertThat(bytesOf(session.processKeyEvent(shiftEnter()))).isEqualTo(csi("27;2;13~"))
      applyModes(connector, csi(">1u"))
      assertThat(bytesOf(session.processKeyEvent(shiftEnter()))).isEqualTo(csi("13;2u"))
    }
  }

  @Test
  fun `alt chords go through the encoder when Alt sends Escape`() = runSessionTest { session, connector, _ ->
    val options = TerminalOptionsProvider.instance
    val useOptionAsMetaKey = options.useOptionAsMetaKey
    options.useOptionAsMetaKey = true // decides the setting on macOS only; elsewhere Alt always sends Escape
    try {
      assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_F, 'ƒ', InputEvent.ALT_DOWN_MASK)))).isEqualTo(Char(27) + "f")
      assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_F, 'F', InputEvent.ALT_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)))).isEqualTo(Char(27) + "F")
      applyModes(connector, csi(">1u"))
      assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_F, 'ƒ', InputEvent.ALT_DOWN_MASK)))).isEqualTo(csi("102;3u"))
    }
    finally {
      options.useOptionAsMetaKey = useOptionAsMetaKey
    }
  }

  @Test
  fun `option composes text on macOS unless it acts as Alt`() {
    Assume.assumeTrue(SystemInfoRt.isMac)
    runSessionTest { session, _, _ ->
      // "Use Option as Meta key" is off by default, so the pressed half types nothing and the typed half types ƒ.
      assertThat(session.processKeyEvent(pressed(KeyEvent.VK_F, 'ƒ', InputEvent.ALT_DOWN_MASK))).isEqualTo(KeyEventProcessingResultDto.Unhandled)
      val result = session.processKeyEvent(typed('ƒ', InputEvent.ALT_DOWN_MASK))
      assertThat(result).isInstanceOf(KeyEventProcessingResultDto.StringResult::class.java)
      assertThat((result as KeyEventProcessingResultDto.StringResult).string).isEqualTo("ƒ")
    }
  }

  @Test
  fun `arrows honor application cursor keys mode`() = runSessionTest { session, connector, _ ->
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_UP)))).isEqualTo(csi("A"))
    applyModes(connector, csi("?1h"))
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_UP)))).isEqualTo(Char(27) + "OA")
  }

  @Test
  fun `ctrl chords produce control bytes`() = runSessionTest { session, _, _ ->
    val result = session.processKeyEvent(pressed(KeyEvent.VK_A, Char(1), InputEvent.CTRL_DOWN_MASK))
    assertThat(bytesOf(result)).isEqualTo(Char(1).toString())
    val space = session.processKeyEvent(pressed(KeyEvent.VK_SPACE, ' ', InputEvent.CTRL_DOWN_MASK))
    assertThat(bytesOf(space)).isEqualTo(Char(0).toString())
  }

  @Test
  fun `ctrl chords carry the character, so fixterms can tell them apart`() = runSessionTest { session, _, _ ->
    val ctrl = InputEvent.CTRL_DOWN_MASK
    val ctrlShift = InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_M, Char(13), ctrlShift)))).isEqualTo(csi("109;6u"))
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_SPACE, ' ', ctrlShift)))).isEqualTo(Char(0).toString())
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_SEMICOLON, ';', ctrl)))).isEqualTo(csi("59;5u"))
  }

  @Test
  fun `ctrl+i, ctrl+m and ctrl+bracket keep their classic bytes outside the Kitty keyboard protocol`() = runSessionTest { session, connector, _ ->
    // fixterms turns them into CSI u chords, which bash, less and fzf cannot read; the classic bytes
    // stay until a program asks for the protocol.
    val ctrl = InputEvent.CTRL_DOWN_MASK
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_I, Char(9), ctrl)))).isEqualTo("\t")
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_M, Char(13), ctrl)))).isEqualTo("\r")
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_OPEN_BRACKET, Char(27), ctrl)))).isEqualTo(Char(27).toString())
    applyModes(connector, csi(">1u"))
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_OPEN_BRACKET, Char(27), ctrl)))).isEqualTo(csi("91;5u"))
  }

  @Test
  fun `ctrl+alt+m keeps the classic byte behind the ESC prefix`() = runSessionTest { session, _, _ ->
    val options = TerminalOptionsProvider.instance
    val useOptionAsMetaKey = options.useOptionAsMetaKey
    options.useOptionAsMetaKey = true // decides the setting on macOS only; elsewhere Alt always sends Escape
    try {
      val ctrlAlt = InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK
      assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_M, Char(13), ctrlAlt)))).isEqualTo(Char(27) + "\r")
    }
    finally {
      options.useOptionAsMetaKey = useOptionAsMetaKey
    }
  }

  @Test
  fun `shift+space is a chord under the Kitty keyboard protocol and a space outside it`() = runSessionTest { session, connector, _ ->
    fun shiftSpace(): String {
      assertThat(session.processKeyEvent(pressed(KeyEvent.VK_SPACE, ' ', InputEvent.SHIFT_DOWN_MASK))).isEqualTo(KeyEventProcessingResultDto.Unhandled)
      val result = session.processKeyEvent(typed(' ', InputEvent.SHIFT_DOWN_MASK))
      assertThat(result).isInstanceOf(KeyEventProcessingResultDto.StringResult::class.java)
      return (result as KeyEventProcessingResultDto.StringResult).string
    }
    assertThat(shiftSpace()).isEqualTo(" ")
    applyModes(connector, csi(">1u"))
    assertThat(shiftSpace()).isEqualTo(csi("32;2u"))
  }

  @Test
  fun `a typed character carries the physical key of its pressed half`() = runSessionTest { session, connector, _ ->
    applyModes(connector, csi(">13u")) // disambiguate + report alternates + report all
    assertThat(session.processKeyEvent(pressed(KeyEvent.VK_2, '@', InputEvent.SHIFT_DOWN_MASK))).isEqualTo(KeyEventProcessingResultDto.Unhandled)
    val result = session.processKeyEvent(typed('@', InputEvent.SHIFT_DOWN_MASK))
    assertThat(result).isInstanceOf(KeyEventProcessingResultDto.StringResult::class.java)
    assertThat((result as KeyEventProcessingResultDto.StringResult).string).isEqualTo(csi("50:64;2u"))
  }

  @Test
  fun `AltGr text is left for KEY_TYPED, not swallowed as a Ctrl-Alt chord`() = runSessionTest { session, _, _ ->
    // Windows AWT reports AltGr as Ctrl+Alt(+AltGraph) down, with keyChar already the AltGr symbol.
    val altGr = InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK or InputEvent.ALT_GRAPH_DOWN_MASK
    assertThat(session.processKeyEvent(pressed(KeyEvent.VK_5, '[', altGr))).isEqualTo(KeyEventProcessingResultDto.Unhandled)
    assertThat(session.processKeyEvent(pressed(KeyEvent.VK_0, '@', altGr))).isEqualTo(KeyEventProcessingResultDto.Unhandled)

    val result = session.processKeyEvent(typed('[', altGr))
    assertThat(result).isInstanceOf(KeyEventProcessingResultDto.StringResult::class.java)
    assertThat((result as KeyEventProcessingResultDto.StringResult).string).isEqualTo("[")
  }

  @Test
  fun `AltGraph alone, without Ctrl, is also AltGr text`() = runSessionTest { session, _, _ ->
    // Linux reports a dedicated AltGr key as AltGraph alone, without synthesizing Ctrl.
    assertThat(session.processKeyEvent(pressed(KeyEvent.VK_5, '[', InputEvent.ALT_GRAPH_DOWN_MASK)))
      .isEqualTo(KeyEventProcessingResultDto.Unhandled)

    val result = session.processKeyEvent(typed('[', InputEvent.ALT_GRAPH_DOWN_MASK))
    assertThat(result).isInstanceOf(KeyEventProcessingResultDto.StringResult::class.java)
    assertThat((result as KeyEventProcessingResultDto.StringResult).string).isEqualTo("[")
  }

  @Test
  fun `a real Ctrl+Alt chord with a control keyChar is not mistaken for AltGr text`() = runSessionTest { session, _, _ ->
    val result = session.processKeyEvent(pressed(KeyEvent.VK_A, Char(1), InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK))
    assertThat(result).isInstanceOf(KeyEventProcessingResultDto.BytesResult::class.java)
  }

  @Test
  fun `a real Ctrl+Alt chord with a printable keyChar is a chord, outside Windows`() {
    // Only Windows synthesizes AltGr as Ctrl+Alt; elsewhere isAltGraphDown alone carries it.
    Assume.assumeFalse(SystemInfoRt.isWindows)
    runSessionTest { session, _, _ ->
      val result = session.processKeyEvent(pressed(KeyEvent.VK_A, 'a', InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK))
      assertThat(result).isInstanceOf(KeyEventProcessingResultDto.BytesResult::class.java)
    }
  }

  @Test
  fun `typed characters are sent as text`() = runSessionTest { session, _, _ ->
    val result = session.processKeyEvent(typed('ф'))
    assertThat(result).isInstanceOf(KeyEventProcessingResultDto.StringResult::class.java)
    assertThat((result as KeyEventProcessingResultDto.StringResult).string).isEqualTo("ф")
  }

  @Test
  fun `shifted characters are sent as text under the Kitty keyboard protocol`() = runSessionTest { session, connector, _ ->
    applyModes(connector, csi(">1u")) // fish pushes "disambiguate escape codes"
    for (ch in listOf('@', 'A', '?')) {
      val result = session.processKeyEvent(typed(ch, InputEvent.SHIFT_DOWN_MASK))
      assertThat(result).isInstanceOf(KeyEventProcessingResultDto.StringResult::class.java)
      assertThat((result as KeyEventProcessingResultDto.StringResult).string).isEqualTo(ch.toString())
    }
  }

  @Test
  fun `key releases and bare modifier keys reach the shell only when the Kitty protocol asks`() = runSessionTest { session, connector, _ ->
    fun leftShift(id: Int = KeyEvent.KEY_PRESSED): KeyEvent {
      val modifiers = if (id == KeyEvent.KEY_PRESSED) InputEvent.SHIFT_DOWN_MASK else 0
      return KeyEvent(eventSource, id, 0, modifiers, KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_LEFT)
    }
    fun typeA() {
      assertThat(session.processKeyEvent(pressed(KeyEvent.VK_A, 'a'))).isEqualTo(KeyEventProcessingResultDto.Unhandled)
      assertThat(session.processKeyEvent(typed('a'))).isInstanceOf(KeyEventProcessingResultDto.StringResult::class.java)
    }

    assertThat(session.processKeyEvent(leftShift())).isEqualTo(KeyEventProcessingResultDto.Unhandled)
    assertThat(session.processKeyEvent(leftShift(KeyEvent.KEY_RELEASED))).isEqualTo(KeyEventProcessingResultDto.Unhandled)
    typeA()
    assertThat(session.processKeyEvent(released(KeyEvent.VK_A, 'a'))).isEqualTo(KeyEventProcessingResultDto.Unhandled)

    applyModes(connector, csi(">3u")) // disambiguate + report events
    assertThat(session.processKeyEvent(leftShift())).isEqualTo(KeyEventProcessingResultDto.Unhandled)
    assertThat(session.processKeyEvent(leftShift(KeyEvent.KEY_RELEASED))).isEqualTo(KeyEventProcessingResultDto.Unhandled)
    typeA()
    assertThat(bytesOf(session.processKeyEvent(released(KeyEvent.VK_A, 'a')))).isEqualTo(csi("97;1:3u"))

    applyModes(connector, csi(">11u")) // + report all
    assertThat(bytesOf(session.processKeyEvent(leftShift()))).isEqualTo(csi("57441;2u"))
  }

  @Test
  fun `the release of AltGr text carries no Ctrl+Alt, as its press did not`() = runSessionTest { session, connector, _ ->
    applyModes(connector, csi(">3u")) // disambiguate + report events
    val altGr = InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK or InputEvent.ALT_GRAPH_DOWN_MASK
    assertThat(session.processKeyEvent(pressed(KeyEvent.VK_Q, '@', altGr))).isEqualTo(KeyEventProcessingResultDto.Unhandled)
    val typed = session.processKeyEvent(typed('@', altGr))
    assertThat(typed).isInstanceOf(KeyEventProcessingResultDto.StringResult::class.java)
    assertThat((typed as KeyEventProcessingResultDto.StringResult).string).isEqualTo("@")
    assertThat(bytesOf(session.processKeyEvent(released(KeyEvent.VK_Q, '@', altGr)))).isEqualTo(csi("64;1:3u"))
  }

  @Test
  fun `a focus loss forgets the held keys`() = runSessionTest { session, connector, _ ->
    applyModes(connector, csi(">3u"))
    assertThat(session.processKeyEvent(pressed(KeyEvent.VK_A, 'a'))).isEqualTo(KeyEventProcessingResultDto.Unhandled)
    assertThat(session.processKeyEvent(typed('a'))).isInstanceOf(KeyEventProcessingResultDto.StringResult::class.java)
    session.focusLost()
    assertThat(session.processKeyEvent(released(KeyEvent.VK_A, 'a'))).isEqualTo(KeyEventProcessingResultDto.Unhandled)
  }

  // ---- mode wiring: a mode the program sets reaches the encoder through the live terminal state ----

  @Test
  fun `DECBKM switches Backspace between DEL and BS`() = runSessionTest { session, connector, _ ->
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_BACK_SPACE, Char(8))))).isEqualTo(Char(127).toString())
    applyModes(connector, csi("?67h"))
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_BACK_SPACE, Char(8))))).isEqualTo(Char(8).toString())
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_BACK_SPACE, Char(8), InputEvent.CTRL_DOWN_MASK)))).isEqualTo(Char(127).toString())
  }

  @Test
  fun `modifyOtherKeys state 2 encodes a modified character as CSI 27`() = runSessionTest { session, connector, _ ->
    applyModes(connector, csi(">4;2m"))
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_H, Char(8), InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)))).isEqualTo(csi("27;6;72~"))
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_P, Char(16), InputEvent.CTRL_DOWN_MASK)))).isEqualTo(csi("27;5;112~"))
  }

  @Test
  fun `mode 1036 reset drops the ESC prefix of an alt chord`() = runSessionTest { session, connector, _ ->
    val options = TerminalOptionsProvider.instance
    val useOptionAsMetaKey = options.useOptionAsMetaKey
    options.useOptionAsMetaKey = true
    try {
      assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_F, 'ƒ', InputEvent.ALT_DOWN_MASK)))).isEqualTo(Char(27) + "f")
      applyModes(connector, csi("?1036l"))
      assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_F, 'ƒ', InputEvent.ALT_DOWN_MASK)))).isEqualTo("f")
    }
    finally {
      options.useOptionAsMetaKey = useOptionAsMetaKey
    }
  }

  @Test
  fun `kitty flags push and pop`() = runSessionTest { session, connector, _ ->
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_ESCAPE, Char(27))))).isEqualTo(Char(27).toString())
    applyModes(connector, csi(">1u"))
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_ESCAPE, Char(27))))).isEqualTo(csi("27u"))
    applyModes(connector, csi("<u"))
    assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_ESCAPE, Char(27))))).isEqualTo(Char(27).toString())
  }

  // ---- policy of this layer ----

  @Test
  fun `the scroll-to-bottom flag follows the result path`() = runSessionTest { session, connector, _ ->
    // "Scroll to the bottom on typing" is on by default: a navigation key, typed text and a chord scroll,
    // a function key and a release do not.
    assertThat(session.processKeyEvent(pressed(KeyEvent.VK_UP)).shouldScrollToBottom).isTrue()
    assertThat(session.processKeyEvent(pressed(KeyEvent.VK_F5)).shouldScrollToBottom).isFalse()
    assertThat(session.processKeyEvent(typed('a')).shouldScrollToBottom).isTrue()
    assertThat(session.processKeyEvent(pressed(KeyEvent.VK_C, Char(3), InputEvent.CTRL_DOWN_MASK)).shouldScrollToBottom).isTrue()
    applyModes(connector, csi(">3u"))
    assertThat(session.processKeyEvent(pressed(KeyEvent.VK_A, 'a'))).isEqualTo(KeyEventProcessingResultDto.Unhandled)
    assertThat(session.processKeyEvent(typed('a')).shouldScrollToBottom).isTrue()
    assertThat(session.processKeyEvent(released(KeyEvent.VK_A, 'a')).shouldScrollToBottom).isFalse()
  }

  @Test
  fun `a command chord types nothing on macOS and stays with the IDE`() {
    Assume.assumeTrue(SystemInfoRt.isMac)
    runSessionTest { session, _, _ ->
      assertThat(session.processKeyEvent(pressed(KeyEvent.VK_B, 'b', InputEvent.META_DOWN_MASK))).isEqualTo(KeyEventProcessingResultDto.Unhandled)
      assertThat(session.processKeyEvent(typed('b', InputEvent.META_DOWN_MASK))).isEqualTo(KeyEventProcessingResultDto.Unhandled)
    }
  }

  @Test
  fun `key releases are left to the IDE`() = runSessionTest { session, _, _ ->
    val release = KeyEvent(eventSource, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED)
    assertThat(session.processKeyEvent(release)).isEqualTo(KeyEventProcessingResultDto.Unhandled)
  }

  @Test
  fun `the Menu key is left to the IDE, which opens the context menu`() = runSessionTest { session, _, _ ->
    assertThat(session.processKeyEvent(pressed(KeyEvent.VK_CONTEXT_MENU))).isEqualTo(KeyEventProcessingResultDto.Unhandled)
  }

  @Test
  fun `cmd and option arrows follow macOS natural text editing`() {
    Assume.assumeTrue(SystemInfoRt.isMac)
    runSessionTest { session, _, _ ->
      assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_LEFT, modifiers = InputEvent.META_DOWN_MASK))))
        .isEqualTo(Char(1).toString()) // Ctrl+A: line start
      assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_RIGHT, modifiers = InputEvent.META_DOWN_MASK))))
        .isEqualTo(Char(5).toString()) // Ctrl+E: line end
      assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_LEFT, modifiers = InputEvent.ALT_DOWN_MASK))))
        .isEqualTo(Char(27) + "b") // backward-word
      assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_RIGHT, modifiers = InputEvent.ALT_DOWN_MASK))))
        .isEqualTo(Char(27) + "f") // forward-word
    }
  }

  @Test
  fun `cmd+backspace kills the line`() {
    Assume.assumeTrue(SystemInfoRt.isMac)
    runSessionTest { session, _, _ ->
      assertThat(bytesOf(session.processKeyEvent(pressed(KeyEvent.VK_BACK_SPACE, '\b', InputEvent.META_DOWN_MASK))))
        .isEqualTo(Char(21).toString()) // Ctrl+U: kill line, not plain Backspace
    }
  }

  // ---- harness ----

  /** Sets the "Send Esc+CR on Shift+Enter" setting until the end of the test. */
  private fun setShiftEnterSendsEscCr(value: Boolean) {
    val settingId = "terminal.shift.enter.sends.esc.cr"
    val previousValue = AdvancedSettings.getBoolean(settingId)
    AdvancedSettings.setBoolean(settingId, value)
    Disposer.register(testDisposable) { AdvancedSettings.setBoolean(settingId, previousValue) }
  }

  private fun bytesOf(result: KeyEventProcessingResultDto): String {
    assertThat(result).isInstanceOf(KeyEventProcessingResultDto.BytesResult::class.java)
    return (result as KeyEventProcessingResultDto.BytesResult).bytes.toString(Charsets.ISO_8859_1)
  }

  private fun pressed(keyCode: Int, keyChar: Char = KeyEvent.CHAR_UNDEFINED, modifiers: Int = 0): KeyEvent =
    KeyEvent(eventSource, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, keyChar)

  private fun typed(keyChar: Char, modifiers: Int = 0): KeyEvent =
    KeyEvent(eventSource, KeyEvent.KEY_TYPED, 0, modifiers, KeyEvent.VK_UNDEFINED, keyChar)

  private fun released(keyCode: Int, keyChar: Char = KeyEvent.CHAR_UNDEFINED, modifiers: Int = 0): KeyEvent =
    KeyEvent(eventSource, KeyEvent.KEY_RELEASED, 0, modifiers, keyCode, keyChar)

  /**
   * Feeds [sequences] to the emulator and waits until they are applied, using a DSR
   * query as a barrier: its reply is produced only after the whole chunk is parsed.
   */
  private fun applyModes(connector: LoopbackTtyConnector, sequences: String) {
    val applied = CountDownLatch(1)
    connector.responseHandler = { applied.countDown() }
    try {
      connector.feed(sequences + csi("6n"))
      assertThat(applied.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        .describedAs("the emulator never processed the injected sequences")
        .isTrue()
    }
    finally {
      connector.responseHandler = null
    }
  }
}
