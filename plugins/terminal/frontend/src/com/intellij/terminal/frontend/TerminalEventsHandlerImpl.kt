// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend

import com.google.common.base.Ascii
import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.BackspaceHandler
import com.intellij.codeInsight.lookup.impl.LookupActionHandler
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.LookupTypedHandler
import com.intellij.ide.DataManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.psi.util.PsiUtilBase
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.session.TerminalState
import com.jediterm.terminal.emulator.mouse.MouseButtonCodes
import com.jediterm.terminal.emulator.mouse.MouseButtonModifierFlags
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalUsageLocalStorage
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
 * Logic of key events handling is copied from [com.jediterm.terminal.ui.TerminalPanel]
 * Logic of mouse event handling is copied from [com.jediterm.terminal.model.JediTerminal]
 */
internal open class TerminalEventsHandlerImpl(
  private val sessionModel: TerminalSessionModel,
  private val editor: EditorEx,
  private val encodingManager: TerminalKeyEncodingManager,
  private val terminalInput: TerminalInput,
  private val settings: JBTerminalSystemSettingsProviderBase,
  private val scrollingModel: TerminalOutputScrollingModel?,
  private val outputModel: TerminalOutputModel,
) : TerminalEventsHandler {
  private var ignoreNextKeyTypedEvent: Boolean = false
  private var lastMotionReport: Point? = null

  private val terminalState: TerminalState
    get() = sessionModel.terminalState.value
  
  private val typeAhead: TerminalTypeAhead?
    get() = editor.getUserData(TerminalTypeAhead.KEY)

  private val vfsSynchronizer: TerminalVfsSynchronizer?
    get() = editor.getUserData(TerminalVfsSynchronizer.KEY)

  override fun keyTyped(e: TimedKeyEvent) {
    LOG.trace { "Key typed event received: ${e.original}" }

    updateLookupOnTyping(e.original.keyChar)
    val selectionModel = editor.selectionModel
    if (selectionModel.hasSelection()) {
      selectionModel.removeSelection()
    }

    if (ignoreNextKeyTypedEvent) {
      e.original.consume()
      LOG.trace { "Key event ignored: ${e.original}" }
      return
    }
    if (!Character.isISOControl(e.original.keyChar)) { // keys filtered out here will be processed in processTerminalKeyPressed
      try {
        if (processCharacter(e)) {
          e.original.consume()
          LOG.trace { "Key event consumed: ${e.original}" }
        }
      }
      catch (ex: Exception) {
        LOG.error("Error sending typed key to emulator", ex)
      }
    }
    val lookup = LookupManager.getActiveLookup(editor)
    // Added to guarantee that the carets are synchronized after type-ahead.
    // Essential for correct lookup behavior.
    val moveCaretAction = { editor.caretModel.moveToOffset(outputModel.cursorOffsetState.value) }
    if (editor.caretModel.offset != outputModel.cursorOffsetState.value) {
      if (lookup != null) {
        lookup.performGuardedChange(moveCaretAction)
      }
      else {
        moveCaretAction()
      }
    }
  }

  override fun keyPressed(e: TimedKeyEvent) {
    LOG.trace { "Key pressed event received: ${e.original}" }
    ignoreNextKeyTypedEvent = false
    if (processTerminalKeyPressed(e)) {
      e.original.consume()
      ignoreNextKeyTypedEvent = true
      LOG.trace { "Key event consumed: ${e.original}" }
    }
  }

  private fun processTerminalKeyPressed(e: TimedKeyEvent): Boolean {
    try {
      val inlineCompletionTypingSession = InlineCompletion.getHandlerOrNull(editor)?.typingSessionTracker
      inlineCompletionTypingSession?.endTypingSession(editor)
      // To invalidate inline completion in case of inputs like backspace, CTRL + C, etc.
      inlineCompletionTypingSession?.ignoreDocumentChanges = false

      vfsSynchronizer?.handleKeyPressed(e.original)

      val keyCode = e.original.keyCode
      val keyChar = e.original.keyChar
      if (isNoModifiers(e.original) && keyCode == KeyEvent.VK_BACK_SPACE) {
        typeAhead?.backspace()
      }
      // All typeAhead updates should be done before calling updateLookupOnAction
      updateLookupOnAction(keyCode)

      // numLock does not change the code sent by keypad VK_DELETE,
      // although it send the char '.'
      if (keyCode == KeyEvent.VK_DELETE && keyChar == '.') {
        terminalInput.sendBytes(byteArrayOf('.'.code.toByte()))
        LOG.trace { "Key event skipped (numLock on): ${e.original}" }
        return true
      }
      // CTRL + Space is not handled in KeyEvent; handle it manually
      if (keyChar == ' ' && e.original.modifiersEx and InputEvent.CTRL_DOWN_MASK != 0) {
        terminalInput.sendBytes(byteArrayOf(Ascii.NUL))
        return true
      }
      val code = encodingManager.getCode(keyCode, e.original.modifiers)
      if (code != null) {
        terminalInput.sendBytes(code)
        if (isCodeThatScrolls(keyCode)) {
          scrollingModel?.scrollToCursor(force = true)
        }
        if (keyCode == KeyEvent.VK_ENTER) {
          TerminalUsageLocalStorage.getInstance().recordEnterKeyPressed()
        }
        return true
      }
      if (isAltPressedOnly(e.original) && Character.isDefined(keyChar) && settings.altSendsEscape()) {
        // Cannot use e.getKeyChar() on macOS:
        //  Option+f produces e.getKeyChar()='ƒ' (402), but 'f' (102) is needed.
        //  Option+b produces e.getKeyChar()='∫' (8747), but 'b' (98) is needed.
        val string = String(charArrayOf(Ascii.ESC.toInt().toChar(), simpleMapKeyCodeToChar(e.original)))
        terminalInput.sendString(string)
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

  private fun processCharacter(e: TimedKeyEvent): Boolean {
    if (isAltPressedOnly(e.original) && settings.altSendsEscape()) {
      LOG.trace { "Key event skipped (alt pressed only): ${e.original}" }
      return false
    }
    val keyChar = e.original.keyChar
    if (keyChar == '`' && e.original.modifiersEx and InputEvent.META_DOWN_MASK != 0) {
      // Command + backtick is a short-cut on Mac OSX, so we shouldn't type anything
      LOG.trace { "Key event skipped (command + backtick): ${e.original}" }
      return false
    }
    val typedString = keyChar.toString()
    if (e.original.id == KeyEvent.KEY_TYPED) {
      val inlineCompletionTypingSession = InlineCompletion.getHandlerOrNull(editor)?.typingSessionTracker
      editor.caretModel.moveToOffset(outputModel.cursorOffsetState.value)
      inlineCompletionTypingSession?.startTypingSession(editor)

      typeAhead?.stringTyped(typedString)
      terminalInput.sendTrackedString(typedString, eventTime = e.initTime)
    }
    else terminalInput.sendString(typedString)

    scrollingModel?.scrollToCursor(force = true)

    return true
  }

  private fun isNoModifiers(e: KeyEvent): Boolean {
    val modifiersEx = e.modifiersEx
    return modifiersEx and InputEvent.ALT_DOWN_MASK == 0
           && modifiersEx and InputEvent.ALT_GRAPH_DOWN_MASK == 0
           && modifiersEx and InputEvent.CTRL_DOWN_MASK == 0
           && modifiersEx and InputEvent.SHIFT_DOWN_MASK == 0
  }

  private fun isAltPressedOnly(e: KeyEvent): Boolean {
    val modifiersEx = e.modifiersEx
    return modifiersEx and InputEvent.ALT_DOWN_MASK != 0
           && modifiersEx and InputEvent.ALT_GRAPH_DOWN_MASK == 0
           && modifiersEx and InputEvent.CTRL_DOWN_MASK == 0
           && modifiersEx and InputEvent.SHIFT_DOWN_MASK == 0
  }

  private fun isCodeThatScrolls(keycode: Int): Boolean {
    return keycode == KeyEvent.VK_UP ||
           keycode == KeyEvent.VK_DOWN ||
           keycode == KeyEvent.VK_LEFT ||
           keycode == KeyEvent.VK_RIGHT ||
           keycode == KeyEvent.VK_BACK_SPACE ||
           keycode == KeyEvent.VK_INSERT ||
           keycode == KeyEvent.VK_DELETE ||
           keycode == KeyEvent.VK_ENTER ||
           keycode == KeyEvent.VK_HOME ||
           keycode == KeyEvent.VK_END ||
           keycode == KeyEvent.VK_PAGE_UP ||
           keycode == KeyEvent.VK_PAGE_DOWN
  }

  private fun updateLookupOnAction(keycode: Int) {
    val caret = editor.getCaretModel().getCurrentCaret()
    val offset = outputModel.cursorOffsetState.value
    val lookup = LookupManager.getActiveLookup(editor) as LookupImpl?
    if (lookup == null) {
      return
    }

    val newOffset = when (keycode) {
      KeyEvent.VK_LEFT -> offset - 1
      KeyEvent.VK_BACK_SPACE -> offset - 1
      else -> offset
    }
    lookup.performGuardedChange(Runnable { editor.caretModel.moveToOffset(newOffset) })

    val handler = when (keycode) {
      KeyEvent.VK_LEFT -> {
        LookupActionHandler.LeftHandler(null)
      }
      KeyEvent.VK_RIGHT -> {
        LookupActionHandler.RightHandler(null)
      }
      KeyEvent.VK_BACK_SPACE -> {
        BackspaceHandler(null)
      }
      else -> return
    }

    handler.execute(editor, caret, DataManager.getInstance().getDataContext(editor.getComponent()))
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
        terminalInput.sendBytes(mouseReport(code, x + 1, y + 1))
      }
    }
  }

  override fun mouseReleased(x: Int, y: Int, event: MouseEvent) {
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
      }
    }
    lastMotionReport = null
  }

  override fun mouseMoved(x: Int, y: Int, event: MouseEvent) {
    if (lastMotionReport == Point(x, y)) {
      return
    }
    if (shouldSendMouseData(MouseMode.MOUSE_REPORTING_ALL_MOTION)) {
      terminalInput.sendBytes(mouseReport(MouseButtonCodes.RELEASE, x + 1, y + 1))
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
        terminalInput.sendBytes(mouseReport(code, x + 1, y + 1))
      }
    }
    lastMotionReport = Point(x, y)
  }

  override fun mouseWheelMoved(x: Int, y: Int, event: MouseWheelEvent) {
    if (settings.enableMouseReporting() && terminalState.mouseMode != MouseMode.MOUSE_REPORTING_NONE && !event.isShiftDown) {
      editor.selectionModel.removeSelection()
      // mousePressed() handles mouse wheel using SCROLLDOWN and SCROLLUP buttons
      mousePressed(x, y, event)
    }
    else if (terminalState.isAlternateScreenBuffer && settings.sendArrowKeysInAlternativeMode()) {
      //Send Arrow keys instead
      val arrowKeys = if (event.wheelRotation < 0) {
        encodingManager.getCode(KeyEvent.VK_UP, 0)
      }
      else {
        encodingManager.getCode(KeyEvent.VK_DOWN, 0)
      }
      for (i in 0 until abs(event.unitsToScroll)) {
        terminalInput.sendBytes(arrowKeys!!)
      }
      event.consume()
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

  private fun updateLookupOnTyping(charTyped: Char) {
    val project = editor.project ?: return
    val lookup = LookupManager.getActiveLookup(editor)
    if (lookup != null) {
      if (charTyped.code != KeyEvent.VK_BACK_SPACE) {
        val psiFile = PsiUtilBase.getPsiFileInEditor(editor, project)
        LookupTypedHandler.beforeCharTyped(
          charTyped,
          project,
          editor,
          editor,
          psiFile,
          Runnable { }
        )
      }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(TerminalEventsHandlerImpl::class.java)
  }
}
