// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.jediterm.core.util.Ascii
import com.jediterm.terminal.TerminalStarter
import com.jediterm.terminal.emulator.mouse.MouseButtonCodes
import com.jediterm.terminal.emulator.mouse.MouseButtonModifierFlags
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.nio.charset.Charset
import javax.swing.SwingUtilities

/**
 * Logic of key events handling is copied from [com.jediterm.terminal.ui.TerminalPanel]
 * Logic of mouse event handling is copied from [com.jediterm.terminal.model.JediTerminal]
 */
class TerminalEventsHandler(private val session: TerminalSession,
                            private val settings: JBTerminalSystemSettingsProviderBase) {
  private var ignoreNextKeyTypedEvent: Boolean = false
  private var lastMotionReport: Point? = null

  private val terminalStarter: TerminalStarter
    // Hope the process is created fast enough - before user starts interacting with the terminal.
    // TODO: buffering events until the process is started will fix the problem.
    get() = session.terminalStarterFuture.getNow(null)!!

  private val model: TerminalModel
    get() = session.model

  fun handleKeyTyped(e: KeyEvent) {
    if (ignoreNextKeyTypedEvent) {
      e.consume()
      return
    }
    if (!Character.isISOControl(e.keyChar)) { // keys filtered out here will be processed in processTerminalKeyPressed
      try {
        if (processCharacter(e)) {
          e.consume()
        }
      }
      catch (ex: Exception) {
        LOG.error("Error sending typed key to emulator", ex)
      }
    }
  }

  fun handleKeyPressed(e: KeyEvent) {
    ignoreNextKeyTypedEvent = false
    if (processTerminalKeyPressed(e)) {
      e.consume()
      ignoreNextKeyTypedEvent = true
    }
  }

  private fun processTerminalKeyPressed(e: KeyEvent): Boolean {
    try {
      val keyCode = e.keyCode
      val keyChar = e.keyChar

      // numLock does not change the code sent by keypad VK_DELETE
      // although it send the char '.'
      if (keyCode == KeyEvent.VK_DELETE && keyChar == '.') {
        terminalStarter.sendBytes(byteArrayOf('.'.code.toByte()), true)
        return true
      }
      // CTRL + Space is not handled in KeyEvent; handle it manually
      if (keyChar == ' ' && e.modifiersEx and InputEvent.CTRL_DOWN_MASK != 0) {
        terminalStarter.sendBytes(byteArrayOf(Ascii.NUL), true)
        return true
      }
      val code = terminalStarter.terminal.getCodeForKey(keyCode, e.modifiers)
      if (code != null) {
        terminalStarter.sendBytes(code, true)
        // TODO
        //if (settings.scrollToBottomOnTyping() && TerminalPanel.isCodeThatScrolls(keyCode)) {
        //  scrollToBottom()
        //}
        return true
      }
      if (isAltPressedOnly(e) && Character.isDefined(keyChar) && settings.altSendsEscape()) {
        // Cannot use e.getKeyChar() on macOS:
        //  Option+f produces e.getKeyChar()='ƒ' (402), but 'f' (102) is needed.
        //  Option+b produces e.getKeyChar()='∫' (8747), but 'b' (98) is needed.
        val string = String(charArrayOf(Ascii.ESC.toInt().toChar(), simpleMapKeyCodeToChar(e)))
        terminalStarter.sendString(string, true)
        return true
      }
      if (Character.isISOControl(keyChar)) { // keys filtered out here will be processed in processTerminalKeyTyped
        return processCharacter(e)
      }
    }
    catch (ex: Exception) {
      LOG.error("Error sending pressed key to emulator", ex)
    }
    return false
  }

  private fun processCharacter(e: KeyEvent): Boolean {
    if (isAltPressedOnly(e) && settings.altSendsEscape()) {
      return false
    }
    val keyChar = e.keyChar
    if (keyChar == '`' && e.modifiersEx and InputEvent.META_DOWN_MASK != 0) {
      // Command + backtick is a short-cut on Mac OSX, so we shouldn't type anything
      return false
    }
    terminalStarter.sendString(keyChar.toString(), true)
    // TODO
    //if (settings.scrollToBottomOnTyping()) {
    //scrollToBottom()
    //}
    return true
  }

  private fun isAltPressedOnly(e: KeyEvent): Boolean {
    val modifiersEx = e.modifiersEx
    return modifiersEx and InputEvent.ALT_DOWN_MASK != 0
           && modifiersEx and InputEvent.ALT_GRAPH_DOWN_MASK == 0
           && modifiersEx and InputEvent.CTRL_DOWN_MASK == 0
           && modifiersEx and InputEvent.SHIFT_DOWN_MASK == 0
  }

  private fun simpleMapKeyCodeToChar(e: KeyEvent): Char {
    // zsh requires proper case of letter
    val keyChar = e.keyCode.toChar()
    return if (e.modifiersEx and InputEvent.SHIFT_DOWN_MASK != 0) {
      keyChar.uppercaseChar()
    }
    else keyChar.lowercaseChar()
  }

  fun handleMousePressed(x: Int, y: Int, event: MouseEvent) {
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
        terminalStarter.sendBytes(mouseReport(code, x + 1, y + 1), true)
      }
    }
  }

  fun handleMouseReleased(x: Int, y: Int, event: MouseEvent) {
    if (shouldSendMouseData(MouseMode.MOUSE_REPORTING_NORMAL, MouseMode.MOUSE_REPORTING_BUTTON_MOTION)) {
      var code = createButtonCode(event)
      if (code != MouseButtonCodes.NONE) {
        code = if (model.mouseFormat == MouseFormat.MOUSE_FORMAT_SGR) {
          // for SGR 1006 mode
          code or MouseButtonModifierFlags.MOUSE_BUTTON_SGR_RELEASE_FLAG
        }
        else {
          // for 1000/1005/1015 mode
          MouseButtonCodes.RELEASE
        }
        code = applyModifierKeys(event, code)
        terminalStarter.sendBytes(mouseReport(code, x + 1, y + 1), true)
      }
    }
    lastMotionReport = null
  }

  fun handleMouseMoved(x: Int, y: Int, event: MouseEvent) {
    if (lastMotionReport == Point(x, y)) {
      return
    }
    if (shouldSendMouseData(MouseMode.MOUSE_REPORTING_ALL_MOTION)) {
      terminalStarter.sendBytes(mouseReport(MouseButtonCodes.RELEASE, x + 1, y + 1), true)
    }
    lastMotionReport = Point(x, y)
  }

  fun handleMouseDragged(x: Int, y: Int, event: MouseEvent) {
    if (lastMotionReport == Point(x, y)) {
      return
    }
    if (shouldSendMouseData(MouseMode.MOUSE_REPORTING_BUTTON_MOTION)) {
      //when dragging, button is not in "button", but in "modifier"
      var code = createButtonCode(event)
      if (code != MouseButtonCodes.NONE) {
        code = code or MouseButtonModifierFlags.MOUSE_BUTTON_MOTION_FLAG
        code = applyModifierKeys(event, code)
        terminalStarter.sendBytes(mouseReport(code, x + 1, y + 1), true)
      }
    }
    lastMotionReport = Point(x, y)
  }

  fun handleMouseWheelMoved(x: Int, y: Int, event: MouseEvent) {
    // mousePressed() handles mouse wheel using SCROLLDOWN and SCROLLUP buttons
    handleMousePressed(x, y, event)
  }

  private fun shouldSendMouseData(vararg eligibleModes: MouseMode): Boolean {
    return when (model.mouseMode) {
      MouseMode.MOUSE_REPORTING_NONE -> false
      MouseMode.MOUSE_REPORTING_ALL_MOTION -> true
      else -> eligibleModes.contains(model.mouseMode)
    }
  }

  private fun createButtonCode(event: MouseEvent): Int {
    // for mouse dragged, button is stored in modifiers
    return when {
      SwingUtilities.isLeftMouseButton(event) -> MouseButtonCodes.LEFT
      SwingUtilities.isMiddleMouseButton(event) -> MouseButtonCodes.MIDDLE
      SwingUtilities.isRightMouseButton(
        event) -> MouseButtonCodes.NONE  //we don't handle right mouse button as it used for the context menu invocation
      event is MouseWheelEvent -> if (event.wheelRotation > 0) MouseButtonCodes.SCROLLUP else MouseButtonCodes.SCROLLDOWN
      else -> return MouseButtonCodes.NONE
    }
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
    val command = when (model.mouseFormat) {
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
    LOG.debug(model.mouseFormat.toString() + " (" + charset + ") report : " + button + ", " + x + "x" + y + " = " + command)
    return command.toByteArray(Charset.forName(charset))
  }

  companion object {
    private val LOG = Logger.getInstance(TerminalEventsHandler::class.java)
  }
}