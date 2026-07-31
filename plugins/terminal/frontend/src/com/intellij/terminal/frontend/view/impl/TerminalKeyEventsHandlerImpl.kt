// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.impl

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.terminal.frontend.view.TerminalInputInterceptor
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalKeyEventImpl
import com.intellij.terminal.frontend.view.typeahead.TerminalTypeAhead
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.block.reworked.TerminalUsageLocalStorage
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.dto.KeyEventProcessingResultDto
import org.jetbrains.plugins.terminal.util.getNow
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
  private val terminalInput: TerminalInput,
  private val scrollingModel: TerminalOutputScrollingModel?,
  private val outputModel: TerminalOutputModel,
  private val typeAhead: TerminalTypeAhead?,
  private val inputInterceptors: () -> List<TerminalInputInterceptor> = { emptyList() },
  private val sessionDeferred: CompletableDeferred<TerminalSession>,
  coroutineScope: CoroutineScope,
) : TerminalKeyEventsHandler {
  private var ignoreNextKeyTypedEvent: Boolean = false
  private val bufferedEvents: ArrayDeque<TimedKeyEvent> = ArrayDeque()
  private var readySession: TerminalSession? = null
  private val sessionInitializationJob: Job?

  init {
    val session = sessionDeferred.getNow()
    if (session == null) {
      sessionInitializationJob = coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
        val session = sessionDeferred.await()
        drainBufferedEvents(session)
        readySession = session
      }
    }
    else {
      readySession = session
      sessionInitializationJob = null
    }
  }

  override fun keyTyped(e: TimedKeyEvent) {
    LOG.trace { "Key typed event received: ${e.original}" }
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
    try {
      val session = readySession
      if (session == null) {
        bufferedEvents.addLast(e)
        e.original.consume()
        LOG.trace { "Key event consumed and buffered until session is ready: ${e.original}" }
      }
      else if (processKeyEventResult(processKeyEvent(e.original, session), e)) {
        e.original.consume()
        LOG.trace { "Key event consumed: ${e.original}" }
      }
      // Keep editor/UI state in sync with the original user input even if session processing is replayed later.
      syncEditorCaretWithModel(editor, outputModel)
      check(keyEventsFlow.tryEmit(TerminalKeyEventImpl(e.original, beforeKeyTypedCursorOffset)))
    }
    catch (ex: Exception) {
      LOG.error("Error sending typed key to emulator", ex)
    }
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
    try {
      val session = readySession
      if (session == null) {
        bufferedEvents.addLast(e)
        e.original.consume()
        LOG.trace { "Key event consumed and buffered until session is ready: ${e.original}" }
      }
      else if (processKeyEventResult(processKeyEvent(e.original, session), e)) {
        e.original.consume()
        ignoreNextKeyTypedEvent = true
        LOG.trace { "Key event consumed: ${e.original}" }
      }
      // Keep editor/UI state in sync with the original user input even if session processing is replayed later.
      syncEditorCaretWithModel(editor, outputModel)
      check(keyEventsFlow.tryEmit(TerminalKeyEventImpl(e.original, beforeKeyPressedCursorOffset)))
    }
    catch (ex: Exception) {
      LOG.error("Error sending pressed key to emulator", ex)
    }
  }

  private fun drainBufferedEvents(readySession: TerminalSession) {
    while (bufferedEvents.isNotEmpty()) {
      val bufferedEvent = bufferedEvents.removeFirst()
      try {
        if (bufferedEvent.original.id == KeyEvent.KEY_TYPED && ignoreNextKeyTypedEvent) {
          ignoreNextKeyTypedEvent = false
          continue
        }
        val result = processKeyEventResult(processKeyEvent(bufferedEvent.original, readySession), bufferedEvent)
        if(result && bufferedEvent.original.id == KeyEvent.KEY_PRESSED) {
          ignoreNextKeyTypedEvent = true
        }
        else ignoreNextKeyTypedEvent = false
      }
      catch (ex: Exception) {
        LOG.error("Error replaying buffered terminal key event", ex)
      }
    }
  }

  private fun processKeyEvent(e: KeyEvent, readySession: TerminalSession): KeyEventProcessingResultDto {
    return try {
      readySession.processKeyEvent(e)
    }
    catch (ex: Exception) {
      LOG.error("Error processing terminal key event", ex)
      KeyEventProcessingResultDto.Unhandled
    }
  }

  private fun processKeyEventResult(result: KeyEventProcessingResultDto, e: TimedKeyEvent): Boolean {
    when (result) {
      KeyEventProcessingResultDto.Unhandled -> return false
      is KeyEventProcessingResultDto.StringResult -> {
        when (e.original.id) {
          KeyEvent.KEY_PRESSED -> terminalInput.sendString(result.string)
          KeyEvent.KEY_TYPED -> {
            typeAhead?.type(result.string)
            terminalInput.sendTrackedString(result.string, eventTime = e.initTime)
          }
        }
      }
      is KeyEventProcessingResultDto.BytesResult -> {
        terminalInput.sendBytes(result.bytes)
      }
    }

    if (result.shouldScrollToBottom) {
      scrollingModel?.scrollToCursor(force = true)
    }

    if (e.original.id == KeyEvent.KEY_PRESSED
        && isNoModifiers(e.original)
        && e.original.keyCode == KeyEvent.VK_BACK_SPACE) {
      typeAhead?.backspace()
    }
    if (e.original.id == KeyEvent.KEY_PRESSED && e.original.keyCode == KeyEvent.VK_ENTER) {
      typeAhead?.type("\n")
      TerminalUsageLocalStorage.getInstance().recordEnterKeyPressed()
    }
    return true
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

  private fun isNoModifiers(e: KeyEvent): Boolean {
    val modifiersEx = e.modifiersEx
    return modifiersEx and InputEvent.ALT_DOWN_MASK == 0
           && modifiersEx and InputEvent.ALT_GRAPH_DOWN_MASK == 0
           && modifiersEx and InputEvent.CTRL_DOWN_MASK == 0
           && modifiersEx and InputEvent.SHIFT_DOWN_MASK == 0
  }

  @TestOnly
  suspend fun awaitBufferedEventsDrained() {
    sessionInitializationJob?.join()
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
