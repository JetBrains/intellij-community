// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.google.common.base.Ascii
import com.intellij.openapi.diagnostic.Logger
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.jediterm.terminal.emulator.mouse.MouseButtonCodes
import com.jediterm.terminal.emulator.mouse.MouseButtonModifierFlags
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import org.jetbrains.plugins.terminal.block.output.TerminalEventsHandler
import org.jetbrains.plugins.terminal.block.output.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import org.jetbrains.plugins.terminal.block.session.TerminalModel
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.nio.charset.Charset
import javax.swing.SwingUtilities
import kotlin.math.abs

/**
 * Handles mouse and keyboard events for terminal.
 * Used in BlockTerminal and AlternateBuffer-Terminal.
 * Logic of key events handling is copied from [com.jediterm.terminal.ui.TerminalPanel]
 * Logic of mouse event handling is copied from [com.jediterm.terminal.model.JediTerminal]
 */
internal open class SimpleTerminalEventsHandler(
  private val session: BlockTerminalSession,
  private val settings: JBTerminalSystemSettingsProviderBase,
  protected val outputModel: TerminalOutputModel,
) : TerminalEventsHandler {
  private var ignoreNextKeyTypedEvent: Boolean = false
  private var lastMotionReport: Point? = null

  private val model: TerminalModel
    get() = session.model

  override fun keyTyped(e: KeyEvent) {
    val selectionModel = outputModel.editor.selectionModel
    if (selectionModel.hasSelection()) {
      selectionModel.removeSelection()
    }

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

  override fun keyPressed(e: KeyEvent) {
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

      // numLock does not change the code sent by keypad VK_DELETE,
      // although it send the char '.'
      if (keyCode == KeyEvent.VK_DELETE && keyChar == '.') {
        sendUserInput(byteArrayOf('.'.code.toByte()))
        return true
      }
      // CTRL + Space is not handled in KeyEvent; handle it manually
      if (keyChar == ' ' && e.modifiersEx and InputEvent.CTRL_DOWN_MASK != 0) {
        sendUserInput(byteArrayOf(Ascii.NUL))
        return true
      }
      val code = session.controller.getCodeForKey(keyCode, e.modifiers)
      if (code != null) {
        sendUserInput(code)
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
        sendUserInput(string)
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
    sendUserInput(keyChar.toString())
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

  override fun mousePressed(x: Int, y: Int, event: MouseEvent) {
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
        sendUserInput(mouseReport(code, x + 1, y + 1))
      }
    }
  }

  override fun mouseReleased(x: Int, y: Int, event: MouseEvent) {
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
        sendUserInput(mouseReport(code, x + 1, y + 1))
      }
    }
    lastMotionReport = null
  }

  override fun mouseMoved(x: Int, y: Int, event: MouseEvent) {
    if (lastMotionReport == Point(x, y)) {
      return
    }
    if (shouldSendMouseData(MouseMode.MOUSE_REPORTING_ALL_MOTION)) {
      sendUserInput(mouseReport(MouseButtonCodes.RELEASE, x + 1, y + 1))
    }
    lastMotionReport = Point(x, y)
  }

  override fun mouseDragged(x: Int, y: Int, event: MouseEvent) {
    if (lastMotionReport == Point(x, y)) {
      return
    }
    if (shouldSendMouseData(MouseMode.MOUSE_REPORTING_BUTTON_MOTION)) {
      //when dragging, button is not in "button", but in "modifier"
      var code = createButtonCode(event)
      if (code != MouseButtonCodes.NONE) {
        code = code or MouseButtonModifierFlags.MOUSE_BUTTON_MOTION_FLAG
        code = applyModifierKeys(event, code)
        sendUserInput(mouseReport(code, x + 1, y + 1))
      }
    }
    lastMotionReport = Point(x, y)
  }

  override fun mouseWheelMoved(x: Int, y: Int, event: MouseWheelEvent) {
    if (settings.enableMouseReporting() && model.mouseMode != MouseMode.MOUSE_REPORTING_NONE && !event.isShiftDown) {
      outputModel.editor.selectionModel.removeSelection()
      // mousePressed() handles mouse wheel using SCROLLDOWN and SCROLLUP buttons
      mousePressed(x, y, event)
    }
    if (model.useAlternateBuffer && settings.sendArrowKeysInAlternativeMode()) {
      //Send Arrow keys instead
      val arrowKeys = if (event.wheelRotation < 0) {
        session.controller.getCodeForKey(KeyEvent.VK_UP, 0)
      }
      else {
        session.controller.getCodeForKey(KeyEvent.VK_DOWN, 0)
      }
      for (i in 0 until abs(event.unitsToScroll)) {
        sendUserInput(arrowKeys)
      }
      event.consume()
    }
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

  private fun sendUserInput(data: String) {
    session.terminalOutputStream.sendString(data, true)
  }

  private fun sendUserInput(bytes: ByteArray) {
    session.terminalOutputStream.sendBytes(bytes, true)
  }

  companion object {
    private val LOG = Logger.getInstance(SimpleTerminalEventsHandler::class.java)
  }
}
