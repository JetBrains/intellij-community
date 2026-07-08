// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.ghostty

import com.intellij.terminal.tests.reworked.util.LoopbackTtyConnector
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JPanel

/**
 * The Ghostty-backed session's
 * [processMouseEvent][org.jetbrains.plugins.terminal.session.impl.TerminalSession.processMouseEvent]:
 * AWT mouse events must be encoded into PTY bytes by the emulator's encoder (so tracking
 * modes are honored), while session-layer policy keeps IDE-owned events (shift-clicks,
 * right clicks, wheel scroll) away from the program.
 *
 * The exact escape sequences per mode are pinned by the emulator module's
 * `MouseEncodingTest`; here the subject is the AWT-to-emulator translation and its
 * policy.
 */
internal class TerminalSessionMouseEventTest : GhosttyTerminalSessionTestCase() {

  private val eventSource = JPanel()

  @Test
  fun `mouse presses are reported only when tracking is requested`() = runSessionTest { session, connector, _ ->
    assertThat(session.processMouseEvent(mouseEvent(MouseEvent.MOUSE_PRESSED), 0, 0)).isNull()

    applyModes(connector, csi("?1000h"))
    val bytes = session.processMouseEvent(mouseEvent(MouseEvent.MOUSE_PRESSED), 0, 0)
    assertThat(bytes).isNotNull()
    assertThat(String(bytes!!, Charsets.ISO_8859_1)).isEqualTo(csi("M") + Char(32) + Char(33) + Char(33))
  }

  @Test
  fun `shift clicks and right clicks are left to the IDE`() = runSessionTest { session, connector, _ ->
    applyModes(connector, csi("?1000h"))
    val shiftClick = mouseEvent(MouseEvent.MOUSE_PRESSED, modifiers = InputEvent.SHIFT_DOWN_MASK)
    assertThat(session.processMouseEvent(shiftClick, 0, 0)).isNull()
    val rightClick = mouseEvent(MouseEvent.MOUSE_PRESSED, button = MouseEvent.BUTTON3)
    assertThat(session.processMouseEvent(rightClick, 0, 0)).isNull()
  }

  @Test
  fun `drags report each cell once`() = runSessionTest { session, connector, _ ->
    applyModes(connector, csi("?1002h")) // button-event tracking
    val drag = mouseEvent(MouseEvent.MOUSE_DRAGGED, modifiers = InputEvent.BUTTON1_DOWN_MASK, button = MouseEvent.NOBUTTON)
    val first = session.processMouseEvent(drag, 2, 1)
    assertThat(first).isNotNull()
    assertThat(String(first!!, Charsets.ISO_8859_1)).isEqualTo(csi("M") + Char(32 + 32) + Char(32 + 3) + Char(32 + 2))
    assertThat(session.processMouseEvent(drag, 2, 1)).isNull() // same cell: AWT motion is per pixel
    assertThat(session.processMouseEvent(drag, 3, 1)).isNotNull()
  }

  @Test
  fun `wheel is reported when tracking is requested`() = runSessionTest { session, connector, _ ->
    applyModes(connector, csi("?1000h"))
    val bytes = session.processMouseEvent(wheel(rotation = -1), 0, 0)
    assertThat(bytes).isNotNull()
    assertThat(String(bytes!!, Charsets.ISO_8859_1)).isEqualTo(csi("M") + Char(32 + 64) + Char(33) + Char(33))
  }

  @Test
  fun `wheel scrolls full-screen programs with arrow keys`() = runSessionTest { session, connector, _ ->
    // Main screen, no tracking: the view scrolls locally, nothing reaches the PTY.
    assertThat(session.processMouseEvent(wheel(rotation = 1), 0, 0)).isNull()

    applyModes(connector, csi("?1049h")) // alternate screen
    val down = session.processMouseEvent(wheel(rotation = 1), 0, 0)
    assertThat(String(down!!, Charsets.ISO_8859_1)).isEqualTo(csi("B").repeat(3))
    val up = session.processMouseEvent(wheel(rotation = -1), 0, 0)
    assertThat(String(up!!, Charsets.ISO_8859_1)).isEqualTo(csi("A").repeat(3))
  }

  // ---- harness ----

  private fun mouseEvent(id: Int, modifiers: Int = 0, button: Int = MouseEvent.BUTTON1): MouseEvent =
    MouseEvent(eventSource, id, 0, modifiers, 0, 0, 1, false, button)

  /**
   * One wheel notch; `unitsToScroll` = 3 * [rotation], matching the usual AWT
   * unit-scroll configuration.
   */
  private fun wheel(rotation: Int): MouseWheelEvent =
    MouseWheelEvent(eventSource, MouseEvent.MOUSE_WHEEL, 0, 0, 0, 0, 0, false,
                    MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, rotation)

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
