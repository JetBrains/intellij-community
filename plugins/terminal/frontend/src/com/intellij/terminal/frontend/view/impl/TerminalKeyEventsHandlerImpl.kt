// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.impl

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalKeyEventImpl
import com.intellij.terminal.frontend.view.TerminalKeyEventsListener
import com.intellij.terminal.frontend.view.typeahead.TerminalTypeAhead
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
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
@ApiStatus.Internal
open class TerminalKeyEventsHandlerImpl(
  private val editor: EditorEx,
  private val terminalInput: TerminalInput,
  private val scrollingModel: TerminalOutputScrollingModel?,
  private val outputModel: TerminalOutputModel,
  private val typeAhead: TerminalTypeAhead?,
  private val keyEventsListeners: List<TerminalKeyEventsListener> = emptyList(),
  private val sessionDeferred: CompletableDeferred<TerminalSession>,
  coroutineScope: CoroutineScope,
) : TerminalKeyEventsHandler {
  private var ignoreNextKeyTypedEvent: Boolean = false
  private val bufferedEvents: ArrayDeque<KeyEvent> = ArrayDeque()
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

  override fun keyTyped(e: KeyEvent) {
    LOG.trace { "Key typed event received: ${e}" }

    if (ignoreNextKeyTypedEvent) {
      e.consume()
      LOG.trace { "Key event ignored: ${e}" }
      return
    }
    val event = TerminalKeyEventImpl(e, outputModel.cursorOffset)
    if (beforeKeyEvent(event)) {
      e.consume()
      LOG.trace { "Key event intercepted: $e" }
      afterKeyEvent(event)
      return
    }
    try {
      val session = readySession
      if (session == null) {
        bufferedEvents.addLast(e)
        e.consume()
        LOG.trace { "Key event consumed and buffered until session is ready: $e" }
      }
      else if (processKeyEventResult(processKeyEvent(e, session), e)) {
        editor.selectionModel.removeSelection(true)
        syncEditorCaretWithModel(editor, outputModel)
        e.consume()
        LOG.trace { "Key event consumed: ${e}" }
      }
      afterKeyEvent(event)
    }
    catch (ex: Exception) {
      LOG.error("Error sending typed key to emulator", ex)
    }
  }

  override fun keyPressed(e: KeyEvent) {
    LOG.trace { "Key pressed event received: ${e}" }

    ignoreNextKeyTypedEvent = false
    val event = TerminalKeyEventImpl(e, outputModel.cursorOffset)
    if (beforeKeyEvent(event)) {
      e.consume()
      ignoreNextKeyTypedEvent = true
      LOG.trace { "Key event intercepted: ${e}" }
      afterKeyEvent(event)
      return
    }
    try {
      val session = readySession
      if (session == null) {
        bufferedEvents.addLast(e)
        e.consume()
        LOG.trace { "Key event consumed and buffered until session is ready: ${e}" }
      }
      else if (processKeyEventResult(processKeyEvent(e, session), e)) {
        editor.selectionModel.removeSelection(true)
        syncEditorCaretWithModel(editor, outputModel)
        e.consume()
        ignoreNextKeyTypedEvent = true
        LOG.trace { "Key event consumed: ${e}" }
      }
      afterKeyEvent(event)
    }
    catch (ex: Exception) {
      LOG.error("Error sending pressed key to emulator", ex)
    }
  }

  private fun drainBufferedEvents(readySession: TerminalSession) {
    while (bufferedEvents.isNotEmpty()) {
      val bufferedEvent = bufferedEvents.removeFirst()
      try {
        if (bufferedEvent.id == KeyEvent.KEY_TYPED && ignoreNextKeyTypedEvent) {
          ignoreNextKeyTypedEvent = false
          continue
        }
        val result = processKeyEventResult(processKeyEvent(bufferedEvent, readySession), bufferedEvent)
        if (result && bufferedEvent.id == KeyEvent.KEY_PRESSED) {
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

  private fun processKeyEventResult(result: KeyEventProcessingResultDto, e: KeyEvent): Boolean {
    when (result) {
      KeyEventProcessingResultDto.Unhandled -> return false
      is KeyEventProcessingResultDto.StringResult -> {
        if (e.id == KeyEvent.KEY_TYPED) {
          typeAhead?.type(result.string)
        }
        terminalInput.sendString(result.string)
      }
      is KeyEventProcessingResultDto.BytesResult -> {
        terminalInput.sendBytes(result.bytes)
      }
    }

    if (result.shouldScrollToBottom) {
      scrollingModel?.scrollToCursor(force = true)
    }

    if (e.id == KeyEvent.KEY_PRESSED
        && isNoModifiers(e)
        && e.keyCode == KeyEvent.VK_BACK_SPACE) {
      typeAhead?.backspace()
    }
    if (e.id == KeyEvent.KEY_PRESSED && e.keyCode == KeyEvent.VK_ENTER) {
      typeAhead?.type("\n")
      TerminalUsageLocalStorage.getInstance().recordEnterKeyPressed()
    }
    return true
  }

  private fun beforeKeyEvent(event: TerminalKeyEvent): Boolean {
    for (listener in keyEventsListeners) {
      try {
        if (listener.beforeKeyEvent(event)) {
          return true
        }
      }
      catch (t: Throwable) {
        LOG.error("Terminal key events listener failed", t)
      }
    }
    return false
  }

  private fun afterKeyEvent(event: TerminalKeyEvent) {
    for (listener in keyEventsListeners) {
      try {
        listener.afterKeyEvent(event)
      }
      catch (t: Throwable) {
        LOG.error("Terminal key events listener failed", t)
      }
    }
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
