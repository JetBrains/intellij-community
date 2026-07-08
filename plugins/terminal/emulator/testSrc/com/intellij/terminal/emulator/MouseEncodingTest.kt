// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import com.jediterm.core.input.MouseEvent
import com.jediterm.core.input.MouseWheelEvent
import com.jediterm.terminal.Terminal
import com.jediterm.terminal.emulator.mouse.MouseButtonCodes
import com.jediterm.terminal.emulator.mouse.MouseButtonModifierFlags
import com.jediterm.terminal.emulator.mouse.MouseEventProcessingSettings
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.emulator.mouse.TerminalMouseEventEncoder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

/**
 * [TerminalEmulator.encodeMouseEvent]: mouse events -> the reports a terminal sends to the PTY, across
 * the tracking modes (X10 / normal / button-event / any-event) and report formats (default, SGR,
 * URXVT, UTF-8) a program can request.
 *
 * Every case asserts the exact bytes against a golden expectation (xterm ctlseqs), and — where JediTerm
 * models the same case — against JediTerm's own [TerminalMouseEventEncoder] output, so the engines
 * cannot drift apart. The tracking mode is set on the emulator with the DECSET the golden documents;
 * the equivalent [MouseMode]/[MouseFormat] pair is passed to JediTerm (see [MouseCase]).
 *
 * Coordinates in [TerminalMouseEvent] are 0-based cells; all report formats are 1-based, so cell (0,0)
 * reports as `1;1`.
 */
class MouseEncodingTest {

  @Test
  fun `no tracking mode means no reports`() = mouse { m ->
    m.assertEncodes("", press(TerminalMouseButton.LEFT, 0, 0))
    m.assertEncodes("", motion(3, 3))
  }

  // ---- normal tracking (DECSET 1000), default xterm format ----

  @Test
  fun `press and release in normal tracking`() = mouse { m ->
    m.normalTracking()
    m.assertEncodes(csi("M") + b(32) + b(33) + b(33), press(TerminalMouseButton.LEFT, 0, 0),
                    jt(MouseEvent.Type.PRESSED, MouseButtonCodes.LEFT))
    m.assertEncodes(csi("M") + b(33) + b(33) + b(33), press(TerminalMouseButton.MIDDLE, 0, 0),
                    jt(MouseEvent.Type.PRESSED, MouseButtonCodes.MIDDLE))
    m.assertEncodes(csi("M") + b(34) + b(33) + b(33), press(TerminalMouseButton.RIGHT, 0, 0),
                    jt(MouseEvent.Type.PRESSED, MouseButtonCodes.RIGHT))
    m.assertEncodes(csi("M") + b(35) + b(33) + b(33), release(TerminalMouseButton.LEFT, 0, 0),
                    jt(MouseEvent.Type.RELEASED, MouseButtonCodes.LEFT))
  }

  @Test
  fun `coordinates are 1-based with a 32 offset`() = mouse { m ->
    m.normalTracking()
    m.assertEncodes(csi("M") + b(32) + b(32 + 5) + b(32 + 3), press(TerminalMouseButton.LEFT, 4, 2),
                    jt(MouseEvent.Type.PRESSED, MouseButtonCodes.LEFT))
  }

  @Test
  fun `wheel steps in normal tracking`() = mouse { m ->
    m.normalTracking()
    m.assertEncodes(csi("M") + b(32 + 64) + b(33) + b(33), press(TerminalMouseButton.WHEEL_UP, 0, 0),
                    jtWheelUp())
    m.assertEncodes(csi("M") + b(32 + 65) + b(33) + b(33), press(TerminalMouseButton.WHEEL_DOWN, 0, 0),
                    jtWheelDown())
  }

  @Test
  fun `modifiers are encoded into the button byte`() = mouse { m ->
    m.normalTracking()
    // No JediTerm cross-check for shift: JediTerm reserves shift-clicks for selection and returns null.
    m.assertEncodes(csi("M") + b(32 + 4) + b(33) + b(33),
                    press(TerminalMouseButton.LEFT, 0, 0, TerminalInputModifier.SHIFT))
    m.assertEncodes(csi("M") + b(32 + 8) + b(33) + b(33),
                    press(TerminalMouseButton.LEFT, 0, 0, TerminalInputModifier.ALT),
                    jt(MouseEvent.Type.PRESSED, MouseButtonCodes.LEFT, MouseButtonModifierFlags.MOUSE_BUTTON_META_FLAG))
    m.assertEncodes(csi("M") + b(32 + 16) + b(33) + b(33),
                    press(TerminalMouseButton.LEFT, 0, 0, TerminalInputModifier.CTRL),
                    jt(MouseEvent.Type.PRESSED, MouseButtonCodes.LEFT, MouseButtonModifierFlags.MOUSE_BUTTON_CTRL_FLAG))
  }

  @Test
  fun `motion is not reported in normal tracking`() = mouse { m ->
    m.normalTracking()
    m.assertEncodes("", drag(1, 1))
    m.assertEncodes("", motion(1, 1))
  }

  // ---- button-event tracking (DECSET 1002) ----

  @Test
  fun `drag is reported in button-event tracking`() = mouse { m ->
    m.buttonTracking()
    m.assertEncodes(csi("M") + b(32 + 32) + b(34) + b(34), drag(1, 1),
                    jt(MouseEvent.Type.DRAGGED, MouseButtonCodes.LEFT, MouseButtonModifierFlags.MOUSE_BUTTON_MOTION_FLAG))
    m.assertEncodes("", motion(1, 1))
  }

  // ---- any-event tracking (DECSET 1003) ----

  @Test
  fun `plain motion is reported in any-event tracking`() = mouse { m ->
    m.anyTracking()
    m.assertEncodes(csi("M") + b(32 + 32 + 3) + b(34) + b(34), motion(1, 1))
  }

  // ---- SGR format (DECSET 1006) ----

  @Test
  fun `press, release, drag and wheel in SGR format`() = mouse { m ->
    m.buttonTracking()
    m.sgrFormat()
    m.assertEncodes(csi("<0;5;3M"), press(TerminalMouseButton.LEFT, 4, 2),
                    jt(MouseEvent.Type.PRESSED, MouseButtonCodes.LEFT))
    m.assertEncodes(csi("<0;5;3m"), release(TerminalMouseButton.LEFT, 4, 2),
                    jt(MouseEvent.Type.RELEASED, MouseButtonCodes.LEFT))
    m.assertEncodes(csi("<32;5;3M"), drag(4, 2),
                    jt(MouseEvent.Type.DRAGGED, MouseButtonCodes.LEFT, MouseButtonModifierFlags.MOUSE_BUTTON_MOTION_FLAG))
    m.assertEncodes(csi("<64;5;3M"), press(TerminalMouseButton.WHEEL_UP, 4, 2), jtWheelUp())
    m.assertEncodes(csi("<65;5;3M"), press(TerminalMouseButton.WHEEL_DOWN, 4, 2), jtWheelDown())
  }

  @Test
  fun `modifiers in SGR format`() = mouse { m ->
    m.normalTracking()
    m.sgrFormat()
    m.assertEncodes(csi("<16;1;1M"), press(TerminalMouseButton.LEFT, 0, 0, TerminalInputModifier.CTRL),
                    jt(MouseEvent.Type.PRESSED, MouseButtonCodes.LEFT, MouseButtonModifierFlags.MOUSE_BUTTON_CTRL_FLAG))
  }

  @Test
  fun `SGR reports large coordinates the default format cannot`() = mouse(columns = 400) { m ->
    m.normalTracking()
    m.sgrFormat()
    m.assertEncodes(csi("<0;300;3M"), press(TerminalMouseButton.LEFT, 299, 2))
  }

  // ---- URXVT format (DECSET 1015) ----

  @Test
  fun `press in URXVT format`() = mouse { m ->
    m.normalTracking()
    m.session.write(csi("?1015h"))
    m.jediTermFormat = MouseFormat.MOUSE_FORMAT_URXVT
    m.assertEncodes(csi("32;5;3M"), press(TerminalMouseButton.LEFT, 4, 2),
                    jt(MouseEvent.Type.PRESSED, MouseButtonCodes.LEFT))
  }

  // ---- UTF-8 format (DECSET 1005) ----

  @Test
  fun `UTF-8 format encodes large coordinates as multi-byte code points`() = mouse(columns = 400) { m ->
    m.normalTracking()
    m.session.write(csi("?1005h"))
    m.jediTermFormat = MouseFormat.MOUSE_FORMAT_XTERM_EXT
    // Column 200 reports as 1-based 201 + 32 = 233 = U+00E9, two bytes in UTF-8.
    val expected = (csi("M") + b(32) + "é" + b(33)).toByteArray(Charsets.UTF_8).toString(Charsets.ISO_8859_1)
    m.assertEncodes(expected, press(TerminalMouseButton.LEFT, 200, 0),
                    jt(MouseEvent.Type.PRESSED, MouseButtonCodes.LEFT))
  }

  // ---- X10 compatibility mode (DECSET 9) ----

  @Test
  fun `X10 mode reports presses without modifiers or releases`() = mouse { m ->
    m.session.write(csi("?9h"))
    m.assertEncodes(csi("M") + b(32) + b(33) + b(33), press(TerminalMouseButton.LEFT, 0, 0))
    m.assertEncodes(csi("M") + b(32) + b(33) + b(33), press(TerminalMouseButton.LEFT, 0, 0, TerminalInputModifier.CTRL))
    m.assertEncodes("", release(TerminalMouseButton.LEFT, 0, 0))
  }

  // ---- event builders ----

  private fun press(button: TerminalMouseButton, column: Int, row: Int, vararg mods: TerminalInputModifier) =
    TerminalMouseEvent(TerminalMouseAction.PRESS, button, column, row, mods.toSet())

  private fun release(button: TerminalMouseButton, column: Int, row: Int, vararg mods: TerminalInputModifier) =
    TerminalMouseEvent(TerminalMouseAction.RELEASE, button, column, row, mods.toSet())

  private fun drag(column: Int, row: Int, button: TerminalMouseButton = TerminalMouseButton.LEFT) =
    TerminalMouseEvent(TerminalMouseAction.MOTION, button, column, row)

  private fun motion(column: Int, row: Int) =
    TerminalMouseEvent(TerminalMouseAction.MOTION, null, column, row)

  /** A byte of the default-format report as a string, e.g. `b(32 + 4)` for a shifted left press. */
  private fun b(value: Int): String = value.toChar().toString()

  private fun jt(type: MouseEvent.Type, buttonCode: Int, modifierFlags: Int = 0): (Int, Int) -> MouseEvent =
    { _, _ -> MouseEvent(type, buttonCode, modifierFlags) }

  // JediTerm's SCROLLUP/SCROLLDOWN constants are named opposite to their wire meaning: its UI layer
  // maps physical wheel-up (negative AWT rotation) to SCROLLDOWN (4), which encodes as the xterm
  // wheel-up code 64. These builders model the physical gesture the way AwtMouseWheelEvent does.
  private fun jtWheelUp(): (Int, Int) -> MouseEvent =
    { _, _ -> MouseWheelEvent(MouseButtonCodes.SCROLLDOWN, 0, -1) }

  private fun jtWheelDown(): (Int, Int) -> MouseEvent =
    { _, _ -> MouseWheelEvent(MouseButtonCodes.SCROLLUP, 0, 1) }

  private fun mouse(columns: Int = 80, rows: Int = 24, block: (MouseCase) -> Unit) =
    session(columns, rows) { session -> block(MouseCase(session)) }

  /**
   * Drives both encoders for one test: the emulator under test and JediTerm's
   * [TerminalMouseEventEncoder] as the reference implementation. The tracking helpers flip the DECSET
   * on the emulator and remember the equivalent [MouseMode] for JediTerm.
   */
  private class MouseCase(val session: EmulatorTestSession) {
    private val jediterm = TerminalMouseEventEncoder()
    private var jediTermMode: MouseMode = MouseMode.MOUSE_REPORTING_NONE
    var jediTermFormat: MouseFormat = MouseFormat.MOUSE_FORMAT_XTERM

    // The encoder consults Terminal.getCodeForKey only when simulating wheel scroll as arrow keys in
    // the alternate screen, which these settings disable; anything else is a test bug.
    private val terminalStub: Terminal = Proxy.newProxyInstance(
      Terminal::class.java.classLoader,
      arrayOf(Terminal::class.java),
    ) { _, method, _ -> throw UnsupportedOperationException("unexpected Terminal call: ${method.name}") } as Terminal

    private val settings = MouseEventProcessingSettings(
      /* isMouseReportingEnabled = */ true,
      /* isUsingAlternateBuffer = */ false,
      /* isSimulateMouseScrollWithArrowKeysInAlternateScreen = */ false,
    )

    fun normalTracking() {
      session.write(csi("?1000h"))
      jediTermMode = MouseMode.MOUSE_REPORTING_NORMAL
    }

    fun buttonTracking() {
      session.write(csi("?1002h"))
      jediTermMode = MouseMode.MOUSE_REPORTING_BUTTON_MOTION
    }

    fun anyTracking() {
      session.write(csi("?1003h"))
      jediTermMode = MouseMode.MOUSE_REPORTING_ALL_MOTION
    }

    fun sgrFormat() {
      session.write(csi("?1006h"))
      jediTermFormat = MouseFormat.MOUSE_FORMAT_SGR
    }

    /**
     * Asserts the emulator encodes the event to [expected], and that JediTerm agrees when
     * [jediTermEvent] is given (a factory taking the 0-based x/y JediTerm expects; it 1-bases the
     * report itself). Cases JediTerm does not model (X10 mode, pure motion, shift-clicks) pass none.
     */
    fun assertEncodes(
      expected: String,
      event: TerminalMouseEvent,
      jediTermEvent: ((x: Int, y: Int) -> MouseEvent)? = null,
    ) {
      val actual = session.emulator.encodeMouseEvent(event).toString(Charsets.ISO_8859_1)
      assertThat(actual.escaped())
        .describedAs("ghostty encoding of ${event.action} ${event.button} at (${event.column}, ${event.row}) mods=${event.modifiers}")
        .isEqualTo(expected.escaped())

      if (jediTermEvent != null) {
        val x = event.column
        val y = event.row
        val jeditermBytes = jediterm.encode(jediTermEvent(x, y), x, y, jediTermMode, jediTermFormat, terminalStub, settings)
        assertThat(jeditermBytes?.toString(Charsets.ISO_8859_1)?.escaped())
          .describedAs("jediterm encoding of ${event.action} ${event.button} at (${event.column}, ${event.row})")
          .isEqualTo(expected.escaped())
      }
    }
  }
}
