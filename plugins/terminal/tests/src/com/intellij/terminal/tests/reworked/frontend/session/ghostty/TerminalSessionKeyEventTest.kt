// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.ghostty

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.terminal.tests.reworked.util.LoopbackTtyConnector
import org.assertj.core.api.Assertions.assertThat
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
  fun `typed characters are sent as text`() = runSessionTest { session, _, _ ->
    val result = session.processKeyEvent(typed('ф'))
    assertThat(result).isInstanceOf(KeyEventProcessingResultDto.StringResult::class.java)
    assertThat((result as KeyEventProcessingResultDto.StringResult).string).isEqualTo("ф")
  }

  @Test
  fun `key releases are left to the IDE`() = runSessionTest { session, _, _ ->
    val release = KeyEvent(eventSource, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED)
    assertThat(session.processKeyEvent(release)).isEqualTo(KeyEventProcessingResultDto.Unhandled)
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

  // ---- harness ----

  private fun bytesOf(result: KeyEventProcessingResultDto): String {
    assertThat(result).isInstanceOf(KeyEventProcessingResultDto.BytesResult::class.java)
    return (result as KeyEventProcessingResultDto.BytesResult).bytes.toString(Charsets.ISO_8859_1)
  }

  private fun pressed(keyCode: Int, keyChar: Char = KeyEvent.CHAR_UNDEFINED, modifiers: Int = 0): KeyEvent =
    KeyEvent(eventSource, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, keyChar)

  private fun typed(keyChar: Char, modifiers: Int = 0): KeyEvent =
    KeyEvent(eventSource, KeyEvent.KEY_TYPED, 0, modifiers, KeyEvent.VK_UNDEFINED, keyChar)

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
