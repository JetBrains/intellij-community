// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session.output

import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.TerminalTextBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.block.session.TerminalModel.Companion.withLock

internal fun createTerminalOutputChannel(
  textBuffer: TerminalTextBuffer,
  terminalDisplay: TerminalDisplayImpl,
  controller: ObservableJediTerminal,
  coroutineScope: CoroutineScope,
): ReceiveChannel<List<TerminalOutputEvent>> {
  val outputChannel = Channel<List<TerminalOutputEvent>>(capacity = Channel.UNLIMITED)

  val discardedHistoryTracker = TerminalDiscardedHistoryTracker(textBuffer)
  val contentChangesTracker = TerminalContentChangesTracker(textBuffer, discardedHistoryTracker)
  val cursorPositionTracker = TerminalCursorPositionTracker(textBuffer, discardedHistoryTracker, terminalDisplay)
  val shellIntegrationController = TerminalShellIntegrationController(controller)

  /**
   * Events should be sent in the following order: content update, cursor position update, other events.
   * This function allows providing content update if it is precalculated, and the other optional event to be sent last.
   */
  fun collectAndSendEvents(contentUpdateEvent: TerminalContentUpdatedEvent?, otherEvent: TerminalOutputEvent?) {
    textBuffer.withLock {
      val contentUpdate = contentUpdateEvent ?: contentChangesTracker.getContentUpdate()
      val cursorPositionUpdate = cursorPositionTracker.getCursorPositionUpdate()
      val updates = listOfNotNull(contentUpdate, cursorPositionUpdate, otherEvent)
      if (updates.isNotEmpty()) {
        outputChannel.trySend(updates)
      }
    }
  }

  coroutineScope.launch(Dispatchers.IO) {
    try {
      while (true) {
        collectAndSendEvents(contentUpdateEvent = null, otherEvent = null)

        delay(10)
      }
    }
    finally {
      outputChannel.close()
    }
  }

  contentChangesTracker.addHistoryOverflowListener { contentUpdate ->
    collectAndSendEvents(contentUpdateEvent = contentUpdate, otherEvent = null)
  }

  shellIntegrationController.addListener(object : TerminalShellIntegrationEventsListener {
    override fun initialized() {
      collectAndSendEvents(contentUpdateEvent = null, otherEvent = TerminalShellIntegrationInitializedEvent)
    }

    override fun commandStarted(command: String) {
      collectAndSendEvents(contentUpdateEvent = null, otherEvent = TerminalCommandStartedEvent(command))
    }

    override fun commandFinished(command: String, exitCode: Int) {
      collectAndSendEvents(contentUpdateEvent = null, otherEvent = TerminalCommandFinishedEvent(command, exitCode))
    }

    override fun promptStarted() {
      collectAndSendEvents(contentUpdateEvent = null, otherEvent = TerminalPromptStartedEvent)
    }

    override fun promptFinished() {
      collectAndSendEvents(contentUpdateEvent = null, otherEvent = TerminalPromptFinishedEvent)
    }
  })

  var curState = TerminalStateDto(
    isCursorVisible = terminalDisplay.isCursorVisible,
    cursorShape = terminalDisplay.cursorShape,
    mouseMode = terminalDisplay.mouseMode,
    mouseFormat = terminalDisplay.mouseFormat,
    isAlternateScreenBuffer = controller.alternativeBufferEnabled,
    isApplicationArrowKeys = controller.applicationArrowKeys,
    isApplicationKeypad = controller.applicationKeypad,
    isAutoNewLine = controller.isAutoNewLine,
    isAltSendsEscape = controller.altSendsEscape,
    isBracketedPasteMode = terminalDisplay.isBracketedPasteMode,
    windowTitle = terminalDisplay.windowTitleText
  )

  controller.addListener(object : JediTerminalListener {
    override fun arrowKeysModeChanged(isApplication: Boolean) {
      textBuffer.withLock {
        curState = curState.copy(isApplicationArrowKeys = isApplication)
        collectAndSendEvents(contentUpdateEvent = null, otherEvent = TerminalStateChangedEvent(curState))
      }
    }

    override fun keypadModeChanged(isApplication: Boolean) {
      textBuffer.withLock {
        curState = curState.copy(isApplicationKeypad = isApplication)
        collectAndSendEvents(contentUpdateEvent = null, otherEvent = TerminalStateChangedEvent(curState))
      }
    }

    override fun autoNewLineChanged(isEnabled: Boolean) {
      textBuffer.withLock {
        curState = curState.copy(isAutoNewLine = isEnabled)
        collectAndSendEvents(contentUpdateEvent = null, otherEvent = TerminalStateChangedEvent(curState))
      }
    }

    override fun altSendsEscapeChanged(isEnabled: Boolean) {
      textBuffer.withLock {
        curState = curState.copy(isAltSendsEscape = isEnabled)
        collectAndSendEvents(contentUpdateEvent = null, otherEvent = TerminalStateChangedEvent(curState))
      }
    }

    /**
     * It is important to collect and send the output state before alternate buffer state is changed in the TextBuffer.
     * Because right after switch, Text Buffer will provide the lines from the buffer of the new state,
     * while currently non-reported changes relate to the previous state.
     */
    override fun beforeAlternateScreenBufferChanged(isEnabled: Boolean) {
      textBuffer.withLock {
        curState = curState.copy(isAlternateScreenBuffer = isEnabled)
        collectAndSendEvents(contentUpdateEvent = null, otherEvent = TerminalStateChangedEvent(curState))
      }
    }
  })

  terminalDisplay.addListener(object : TerminalDisplayListener {
    override fun cursorVisibilityChanged(isVisible: Boolean) {
      textBuffer.withLock {
        curState = curState.copy(isCursorVisible = isVisible)
        collectAndSendEvents(contentUpdateEvent = null, otherEvent = TerminalStateChangedEvent(curState))
      }
    }

    override fun cursorShapeChanged(cursorShape: CursorShape?) {
      textBuffer.withLock {
        curState = curState.copy(cursorShape = cursorShape)
        collectAndSendEvents(contentUpdateEvent = null, otherEvent = TerminalStateChangedEvent(curState))
      }
    }

    override fun mouseModeChanged(mode: MouseMode) {
      textBuffer.withLock {
        curState = curState.copy(mouseMode = mode)
        collectAndSendEvents(contentUpdateEvent = null, otherEvent = TerminalStateChangedEvent(curState))
      }
    }

    override fun mouseFormatChanged(format: MouseFormat) {
      textBuffer.withLock {
        curState = curState.copy(mouseFormat = format)
        collectAndSendEvents(contentUpdateEvent = null, otherEvent = TerminalStateChangedEvent(curState))
      }
    }

    override fun bracketedPasteModeChanged(isEnabled: Boolean) {
      textBuffer.withLock {
        curState = curState.copy(isBracketedPasteMode = isEnabled)
        collectAndSendEvents(contentUpdateEvent = null, otherEvent = TerminalStateChangedEvent(curState))
      }
    }

    override fun windowTitleChanged(title: String) {
      textBuffer.withLock {
        curState = curState.copy(windowTitle = title)
        collectAndSendEvents(contentUpdateEvent = null, otherEvent = TerminalStateChangedEvent(curState))
      }
    }

    override fun beep() {
      textBuffer.withLock {
        collectAndSendEvents(contentUpdateEvent = null, otherEvent = TerminalBeepEvent)
      }
    }
  })

  return outputChannel
}