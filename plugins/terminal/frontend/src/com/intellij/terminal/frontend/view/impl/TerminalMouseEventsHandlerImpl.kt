package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.jediterm.terminal.emulator.mouse.MouseButtonCodes
import com.jediterm.terminal.emulator.mouse.MouseButtonModifierFlags
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.session.impl.TerminalState
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.nio.charset.Charset
import javax.swing.SwingUtilities
import kotlin.math.abs

internal class TerminalMouseEventsHandlerImpl(
  private val editor: Editor,
  private val terminalInput: TerminalInput,
  private val sessionModel: TerminalSessionModel,
  private val encodingManager: TerminalKeyEncodingManager,
  private val settings: JBTerminalSystemSettingsProviderBase,
) : TerminalMouseEventsHandler {
  private val terminalState: TerminalState
    get() = sessionModel.terminalState.value

  private var lastMotionReport: Point? = null

  override fun mousePressed(x: Int, y: Int, event: MouseEvent) {
    // Some other handler may already consume this event, for example, hyperlinks logic.
    // Do not send a mouse report to the process in this case.
    if (event.isConsumed) return

    if (shouldSendMouseData(MouseMode.MOUSE_REPORTING_NORMAL, MouseMode.MOUSE_REPORTING_BUTTON_MOTION)) {
      var code = createButtonCode(event)
      if (code != MouseButtonCodes.NONE) {
        if (code == MouseButtonCodes.SCROLLDOWN || code == MouseButtonCodes.SCROLLUP) {
          // convert x11 scroll button number to terminal button code
          val offset = MouseButtonCodes.SCROLLDOWN
          code -= offset
          code = code or MouseButtonModifierFlags.MOUSE_BUTTON_SCROLL_FLAG
        }
        code = applyModifierKeys(event, code)
        terminalInput.sendBytes(mouseReport(code, x + 1, y + 1))

        // Editor selection can be active at this moment only if the user holds Shift.
        // Support the case of removing the selection here, because editor logic won't be able to do it
        // (we consume the event).
        editor.selectionModel.removeSelection()

        // Consume the mouse event to avoid double processing:
        // by the terminal process and the editor logic (for example, text selection).
        event.consume()
      }
    }
  }

  override fun mouseReleased(x: Int, y: Int, event: MouseEvent) {
    // Some other handler may already consume this event, for example, hyperlinks logic.
    // Do not send a mouse report to the process in this case.
    if (event.isConsumed) return

    if (shouldSendMouseData(MouseMode.MOUSE_REPORTING_NORMAL, MouseMode.MOUSE_REPORTING_BUTTON_MOTION)) {
      var code = createButtonCode(event)
      if (code != MouseButtonCodes.NONE) {
        code = if (terminalState.mouseFormat == MouseFormat.MOUSE_FORMAT_SGR) {
          // for SGR 1006 mode
          code or MouseButtonModifierFlags.MOUSE_BUTTON_SGR_RELEASE_FLAG
        }
        else {
          // for 1000/1005/1015 mode
          MouseButtonCodes.RELEASE
        }
        code = applyModifierKeys(event, code)
        terminalInput.sendBytes(mouseReport(code, x + 1, y + 1))

        // Consume the mouse event to avoid double processing:
        // by the terminal process and the editor logic (for example, text selection).
        event.consume()
      }
    }
    lastMotionReport = null
  }

  override fun mouseMoved(x: Int, y: Int, event: MouseEvent) {
    // Some other handler may already consume this event, for example, hyperlinks logic.
    // Do not send a mouse report to the process in this case.
    if (event.isConsumed) return

    if (lastMotionReport == Point(x, y)) {
      return
    }
    if (shouldSendMouseData(MouseMode.MOUSE_REPORTING_ALL_MOTION)) {
      terminalInput.sendBytes(
        mouseReport(MouseButtonCodes.RELEASE or MouseButtonModifierFlags.MOUSE_BUTTON_MOTION_FLAG, x + 1, y + 1)
      )

      // Consume the mouse event to avoid double processing:
      // by the terminal process and the editor logic (for example, text selection).
      event.consume()
    }
    lastMotionReport = Point(x, y)
  }

  override fun mouseDragged(x: Int, y: Int, event: MouseEvent) {
    // Some other handler may already consume this event, for example, hyperlinks logic.
    // Do not send a mouse report to the process in this case.
    if (event.isConsumed) return

    if (lastMotionReport == Point(x, y)) {
      return
    }
    if (shouldSendMouseData(MouseMode.MOUSE_REPORTING_BUTTON_MOTION)) {
      //when dragging, the button is not in "button", but in "modifier"
      var code = createButtonCode(event)
      if (code != MouseButtonCodes.NONE) {
        code = code or MouseButtonModifierFlags.MOUSE_BUTTON_MOTION_FLAG
        code = applyModifierKeys(event, code)
        terminalInput.sendBytes(mouseReport(code, x + 1, y + 1))

        // Consume the mouse event to avoid double processing:
        // by the terminal process and the editor logic (for example, text selection).
        event.consume()
      }
    }
    lastMotionReport = Point(x, y)
  }

  override fun mouseWheelMoved(x: Int, y: Int, event: MouseWheelEvent) {
    // Some other handler may already consume this event, for example, hyperlinks logic.
    // Do not send a mouse report to the process in this case.
    if (event.isConsumed) return

    if (settings.enableMouseReporting() && terminalState.mouseMode != MouseMode.MOUSE_REPORTING_NONE && !event.isShiftDown) {
      editor.selectionModel.removeSelection()
      // mousePressed() handles mouse wheel using SCROLLDOWN and SCROLLUP buttons
      mousePressed(x, y, event)
    }
    else if (terminalState.isAlternateScreenBuffer &&
             settings.simulateMouseScrollWithArrowKeysInAlternativeScreen() &&
             !event.isShiftDown /* skip horizontal scrolls */) {
      // Send Arrow keys instead
      val arrowKeys: ByteArray? = when {
        event.wheelRotation < 0 -> encodingManager.getCode(KeyEvent.VK_UP, 0)
        event.wheelRotation > 0 -> encodingManager.getCode(KeyEvent.VK_DOWN, 0)
        else -> null
      }
      if (arrowKeys != null) {
        repeat(abs(event.unitsToScroll)) {
          terminalInput.sendBytes(arrowKeys)
        }

        // Consume the mouse event to avoid double processing:
        // by the terminal process and the editor logic (for example, text selection).
        event.consume()
      }
    }
  }

  private fun shouldSendMouseData(vararg eligibleModes: MouseMode): Boolean {
    val mode = terminalState.mouseMode
    return when (mode) {
      MouseMode.MOUSE_REPORTING_NONE -> false
      MouseMode.MOUSE_REPORTING_ALL_MOTION -> true
      else -> eligibleModes.contains(mode)
    }
  }

  private fun createButtonCode(event: MouseEvent): Int {
    // for mouse dragged, the button is stored in modifiers
    return when {
      SwingUtilities.isLeftMouseButton(event) -> MouseButtonCodes.LEFT
      SwingUtilities.isMiddleMouseButton(event) -> MouseButtonCodes.MIDDLE
      SwingUtilities.isRightMouseButton(event) -> {
        // we don't handle the right mouse button as it used for the context menu invocation
        MouseButtonCodes.NONE
      }
      event is MouseWheelEvent -> wheelRotationToButtonCode(event.wheelRotation)
      else -> MouseButtonCodes.NONE
    }
  }

  private fun wheelRotationToButtonCode(wheelRotation: Int): Int = when {
    wheelRotation > 0 -> MouseButtonCodes.SCROLLUP
    wheelRotation < 0 -> MouseButtonCodes.SCROLLDOWN
    else -> MouseButtonCodes.NONE
  }

  private fun applyModifierKeys(event: MouseEvent, cb: Int): Int {
    var code = cb
    if (event.isControlDown) {
      code = code or MouseButtonModifierFlags.MOUSE_BUTTON_CTRL_FLAG
    }
    if (event.isShiftDown) {
      code = code or MouseButtonModifierFlags.MOUSE_BUTTON_SHIFT_FLAG
    }
    if (event.modifiersEx and InputEvent.META_MASK != 0) {
      code = code or MouseButtonModifierFlags.MOUSE_BUTTON_META_FLAG
    }
    return code
  }

  private fun mouseReport(button: Int, x: Int, y: Int): ByteArray {
    var charset = "UTF-8" // extended mode requires UTF-8 encoding
    val mouseFormat = terminalState.mouseFormat
    val command = when (mouseFormat) {
      MouseFormat.MOUSE_FORMAT_XTERM_EXT -> {
        String.format("\u001b[M%c%c%c", (32 + button).toChar(), (32 + x).toChar(), (32 + y).toChar())
      }
      MouseFormat.MOUSE_FORMAT_URXVT -> {
        String.format("\u001b[%d;%d;%dM", 32 + button, x, y)
      }
      MouseFormat.MOUSE_FORMAT_SGR -> {
        if (button and MouseButtonModifierFlags.MOUSE_BUTTON_SGR_RELEASE_FLAG != 0) {
          // for mouse release event
          String.format("\u001b[<%d;%d;%dm",
                        button xor MouseButtonModifierFlags.MOUSE_BUTTON_SGR_RELEASE_FLAG,
                        x,
                        y)
        }
        else {
          // for mouse press/motion event
          String.format("\u001b[<%d;%d;%dM", button, x, y)
        }
      }
      else -> {
        // X10 compatibility mode requires ASCII
        // US-ASCII is only 7 bits, so we use ISO-8859-1 (8 bits with ASCII transparency)
        // to handle positions greater than 95 (= 127-32)
        charset = "ISO-8859-1"
        String.format("\u001b[M%c%c%c", (32 + button).toChar(), (32 + x).toChar(), (32 + y).toChar())
      }
    }
    LOG.debug(mouseFormat.toString() + " (" + charset + ") report : " + button + ", " + x + "x" + y + " = " + command)
    return command.toByteArray(Charset.forName(charset))
  }

  companion object {
    private val LOG = logger<TerminalMouseEventsHandlerImpl>()
  }
}
