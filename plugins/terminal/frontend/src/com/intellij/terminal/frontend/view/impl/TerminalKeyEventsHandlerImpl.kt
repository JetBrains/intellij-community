// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.impl

import com.google.common.base.Ascii
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.frontend.view.TerminalInputInterceptor
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalKeyEventImpl
import com.intellij.terminal.frontend.view.typeahead.TerminalTypeAhead
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.plugins.terminal.block.reworked.TerminalUsageLocalStorage
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

/**
 * Handles mouse and keyboard events for terminal.
 * Logic of key events handling is copied from [com.jediterm.terminal.ui.TerminalPanel]
 * Logic of mouse event handling is copied from [com.jediterm.terminal.model.JediTerminal]
 */
internal open class TerminalKeyEventsHandlerImpl(
  private val keyEventsFlow: MutableSharedFlow<TerminalKeyEvent>,
  private val editor: EditorEx,
  private val encodingManager: TerminalKeyEncodingManager,
  private val terminalInput: TerminalInput,
  private val settings: JBTerminalSystemSettingsProviderBase,
  private val scrollingModel: TerminalOutputScrollingModel?,
  private val outputModel: TerminalOutputModel,
  private val typeAhead: TerminalTypeAhead?,
  private val inputInterceptors: () -> List<TerminalInputInterceptor> = { emptyList() },
) : TerminalKeyEventsHandler {
  private var ignoreNextKeyTypedEvent: Boolean = false

  override fun keyTyped(e: TimedKeyEvent) {
    LOG.trace { "Key typed event received: ${e.original}" }
    val charTyped = e.original.keyChar
    val beforeKeyTypedCursorOffset = outputModel.cursorOffset

    if (ignoreNextKeyTypedEvent) {
      e.original.consume()
      LOG.trace { "Key event ignored: ${e.original}" }
      return
    }
    if (interceptTerminalInput(e.original)) {
      e.original.consume()
      LOG.trace { "Key event intercepted: ${e.original}" }
      check(keyEventsFlow.tryEmit(TerminalKeyEventImpl(e.original, beforeKeyTypedCursorOffset)))
      return
    }
    if (!Character.isISOControl(charTyped)) { // keys filtered out here will be processed in processTerminalKeyPressed
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

    syncEditorCaretWithModel(editor, outputModel)
    check(keyEventsFlow.tryEmit(TerminalKeyEventImpl(e.original, beforeKeyTypedCursorOffset)))
  }

  override fun keyPressed(e: TimedKeyEvent) {
    LOG.trace { "Key pressed event received: ${e.original}" }
    val beforeKeyPressedCursorOffset = outputModel.cursorOffset

    ignoreNextKeyTypedEvent = false
    if (interceptTerminalInput(e.original)) {
      e.original.consume()
      ignoreNextKeyTypedEvent = true
      LOG.trace { "Key event intercepted: ${e.original}" }
      check(keyEventsFlow.tryEmit(TerminalKeyEventImpl(e.original, beforeKeyPressedCursorOffset)))
      return
    }
    if (processTerminalKeyPressed(e)) {
      e.original.consume()
      ignoreNextKeyTypedEvent = true
      LOG.trace { "Key event consumed: ${e.original}" }
    }

    check(keyEventsFlow.tryEmit(TerminalKeyEventImpl(e.original, beforeKeyPressedCursorOffset)))
  }

  private fun processTerminalKeyPressed(e: TimedKeyEvent): Boolean {
    try {
      val keyCode = e.original.keyCode
      val keyChar = e.original.keyChar
      if (isNoModifiers(e.original) && keyCode == KeyEvent.VK_BACK_SPACE) {
        typeAhead?.backspace()
      }

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

      // Shift+Enter handling as Esc+CR
      if (settings.shiftEnterSendsEscCR()
          && keyCode == KeyEvent.VK_ENTER && isShiftPressedOnly(e.original)) {
        terminalInput.sendBytes(byteArrayOf(Ascii.ESC, Ascii.CR))
        return true
      }

      @Suppress("DEPRECATION")
      val code = encodingManager.getCode(keyCode, e.original.modifiers)
      if (code != null) {
        terminalInput.sendBytes(code)
        if (isCodeThatScrolls(keyCode)) {
          scrollingModel?.scrollToCursor(force = true)
        }
        if (keyCode == KeyEvent.VK_ENTER) {
          typeAhead?.type("\n")
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
    finally {
      syncEditorCaretWithModel(editor, outputModel)
    }
    return false
  }

  private fun interceptTerminalInput(event: KeyEvent): Boolean {
    for (interceptor in inputInterceptors()) {
      try {
        if (interceptor.beforeTerminalInput(event)) {
          return true
        }
      }
      catch (t: Throwable) {
        LOG.error("Terminal input interceptor failed", t)
      }
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
      typeAhead?.type(typedString)
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

  private fun isShiftPressedOnly(e: KeyEvent): Boolean {
    val modifiersEx = e.modifiersEx
    return modifiersEx and InputEvent.SHIFT_DOWN_MASK != 0
           && modifiersEx and InputEvent.ALT_DOWN_MASK == 0
           && modifiersEx and InputEvent.ALT_GRAPH_DOWN_MASK == 0
           && modifiersEx and InputEvent.CTRL_DOWN_MASK == 0
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

  private fun simpleMapKeyCodeToChar(e: KeyEvent): Char {
    // zsh requires proper case of letter
    val keyChar = e.keyCode.toChar()
    return if (e.modifiersEx and InputEvent.SHIFT_DOWN_MASK != 0) {
      keyChar.uppercaseChar()
    }
    else keyChar.lowercaseChar()
  }

  companion object {
    private val LOG = Logger.getInstance(TerminalKeyEventsHandlerImpl::class.java)
  }
}


/**
 * Guarantee that the editor caret is synchronized with the output model's cursor offset.
 * Essential for correct lookup behavior.
 */
internal fun syncEditorCaretWithModel(editor: EditorEx, outputModel: TerminalOutputModel) {
  val expectedCaretOffset = outputModel.cursorOffset.toRelative(outputModel)
  val moveCaretAction = { editor.caretModel.moveToOffset(expectedCaretOffset) }
  if (editor.caretModel.offset != expectedCaretOffset) {
    val lookup = LookupManager.getActiveLookup(editor)
    if (lookup != null) {
      lookup.performGuardedChange(moveCaretAction)
    }
    else {
      moveCaretAction()
    }
  }
}