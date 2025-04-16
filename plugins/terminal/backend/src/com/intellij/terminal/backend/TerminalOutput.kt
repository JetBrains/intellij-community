// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend

import com.intellij.terminal.session.*
import com.intellij.terminal.session.dto.toDto
import com.intellij.util.asDisposable
import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.plugins.terminal.block.reworked.TerminalShellIntegrationEventsListener
import org.jetbrains.plugins.terminal.block.ui.withLock
import java.util.concurrent.atomic.AtomicInteger

private val outputUpdateEventIdCounter = AtomicInteger(0)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun createTerminalOutputFlow(
  services: JediTermServices,
  shellIntegrationController: TerminalShellIntegrationController,
  coroutineScope: CoroutineScope,
): MutableSharedFlow<List<TerminalOutputEvent>> {
  val outputFlow = MutableSharedFlow<List<TerminalOutputEvent>>(
    // Do not buffer a lot of events here.
    // If the buffer is not small, then on large outputs it will always be filled up.
    // So, the UI will need some time to handle the buffered events.
    // In this case, new events, emitted in response to user actions (like Ctrl+C), will be placed to the buffer end
    // and collected from it with only with a noticeable delay.
    // By having a small buffer, we ensure that response to user actions will be collected as soon as possible.
    replay = 1,
    extraBufferCapacity = 0,
    onBufferOverflow = BufferOverflow.SUSPEND
  )

  val textBuffer = services.textBuffer
  val controller = services.controller
  val terminalDisplay = services.terminalDisplay

  val discardedHistoryTracker = TerminalDiscardedHistoryTracker(textBuffer)
  val contentChangesTracker = TerminalContentChangesTracker(textBuffer, discardedHistoryTracker)
  val cursorPositionTracker = TerminalCursorPositionTracker(textBuffer, discardedHistoryTracker, terminalDisplay)
  val outputLatencyTracker = TerminalOutputLatencyTracker(services.ttyConnector, textBuffer, coroutineScope.asDisposable())

  /**
   * Events should be sent in the following order: content update, cursor position update, other events.
   * This function allows providing content update if it is precalculated, and the other optional event to be sent last.
   */
  fun collectAndSendEvents(
    contentUpdate: TerminalContentUpdate?,
    otherEvent: TerminalOutputEvent?,
    ensureActive: () -> Unit = { ensureEmulationActive(services.terminalStarter) },
  ) {
    textBuffer.withLock {
      val actualContentUpdate = contentUpdate ?: contentChangesTracker.getContentUpdate()
      val contentUpdateEvent = if (actualContentUpdate != null) {
        TerminalContentUpdatedEvent(
          id = outputUpdateEventIdCounter.getAndIncrement(),
          text = actualContentUpdate.text,
          styles = actualContentUpdate.styles,
          startLineLogicalIndex = actualContentUpdate.startLineLogicalIndex,
          readTime = outputLatencyTracker.getCurUpdateTtyReadTimeAndReset(),
        )
      }
      else null

      val cursorPositionUpdate = cursorPositionTracker.getCursorPositionUpdate()
      val updates = listOfNotNull(contentUpdateEvent, cursorPositionUpdate, otherEvent)
      if (updates.isNotEmpty()) {
        // Block the shell output reading if any of the following:
        // 1. There are no active collectors: then there is no need to read the shell output.
        // 2. Previous output updates are not collected yet.
        while (outputFlow.subscriptionCount.value == 0 || !outputFlow.tryEmit(updates)) {
          Thread.sleep(1)
          ensureActive()
        }
      }
    }
  }

  coroutineScope.launch(Dispatchers.IO) {
    while (true) {
      collectAndSendEvents(
        contentUpdate = null,
        otherEvent = null,
        ensureActive = { ensureActive(); ensureEmulationActive(services.terminalStarter) }
      )

      delay(10)
    }
  }

  contentChangesTracker.addHistoryOverflowListener { contentUpdate ->
    collectAndSendEvents(contentUpdate = contentUpdate, otherEvent = null)
  }

  var curState = TerminalState(
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
    windowTitle = terminalDisplay.windowTitleText,
    isShellIntegrationEnabled = false
  )

  controller.addListener(object : JediTerminalListener {
    override fun arrowKeysModeChanged(isApplication: Boolean) {
      textBuffer.withLock {
        curState = curState.copy(isApplicationArrowKeys = isApplication)
        collectAndSendEvents(contentUpdate = null, otherEvent = TerminalStateChangedEvent(curState.toDto()))
      }
    }

    override fun keypadModeChanged(isApplication: Boolean) {
      textBuffer.withLock {
        curState = curState.copy(isApplicationKeypad = isApplication)
        collectAndSendEvents(contentUpdate = null, otherEvent = TerminalStateChangedEvent(curState.toDto()))
      }
    }

    override fun autoNewLineChanged(isEnabled: Boolean) {
      textBuffer.withLock {
        curState = curState.copy(isAutoNewLine = isEnabled)
        collectAndSendEvents(contentUpdate = null, otherEvent = TerminalStateChangedEvent(curState.toDto()))
      }
    }

    override fun altSendsEscapeChanged(isEnabled: Boolean) {
      textBuffer.withLock {
        curState = curState.copy(isAltSendsEscape = isEnabled)
        collectAndSendEvents(contentUpdate = null, otherEvent = TerminalStateChangedEvent(curState.toDto()))
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
        collectAndSendEvents(contentUpdate = null, otherEvent = TerminalStateChangedEvent(curState.toDto()))
      }
    }
  })

  terminalDisplay.addListener(object : TerminalDisplayListener {
    override fun cursorVisibilityChanged(isVisible: Boolean) {
      textBuffer.withLock {
        curState = curState.copy(isCursorVisible = isVisible)
        collectAndSendEvents(contentUpdate = null, otherEvent = TerminalStateChangedEvent(curState.toDto()))
      }
    }

    override fun cursorShapeChanged(cursorShape: CursorShape?) {
      textBuffer.withLock {
        curState = curState.copy(cursorShape = cursorShape)
        collectAndSendEvents(contentUpdate = null, otherEvent = TerminalStateChangedEvent(curState.toDto()))
      }
    }

    override fun mouseModeChanged(mode: MouseMode) {
      textBuffer.withLock {
        curState = curState.copy(mouseMode = mode)
        collectAndSendEvents(contentUpdate = null, otherEvent = TerminalStateChangedEvent(curState.toDto()))
      }
    }

    override fun mouseFormatChanged(format: MouseFormat) {
      textBuffer.withLock {
        curState = curState.copy(mouseFormat = format)
        collectAndSendEvents(contentUpdate = null, otherEvent = TerminalStateChangedEvent(curState.toDto()))
      }
    }

    override fun bracketedPasteModeChanged(isEnabled: Boolean) {
      textBuffer.withLock {
        curState = curState.copy(isBracketedPasteMode = isEnabled)
        collectAndSendEvents(contentUpdate = null, otherEvent = TerminalStateChangedEvent(curState.toDto()))
      }
    }

    override fun windowTitleChanged(title: String) {
      textBuffer.withLock {
        curState = curState.copy(windowTitle = title)
        collectAndSendEvents(contentUpdate = null, otherEvent = TerminalStateChangedEvent(curState.toDto()))
      }
    }

    override fun beep() {
      textBuffer.withLock {
        collectAndSendEvents(contentUpdate = null, otherEvent = TerminalBeepEvent)
      }
    }
  })

  shellIntegrationController.addListener(object : TerminalShellIntegrationEventsListener {
    override fun initialized() {
      textBuffer.withLock {
        curState = curState.copy(isShellIntegrationEnabled = true)
        collectAndSendEvents(contentUpdate = null, otherEvent = TerminalStateChangedEvent(curState.toDto()))
      }
    }

    override fun commandStarted(command: String) {
      collectAndSendEvents(contentUpdate = null, otherEvent = TerminalCommandStartedEvent(command))
    }

    override fun commandFinished(command: String, exitCode: Int) {
      collectAndSendEvents(contentUpdate = null, otherEvent = TerminalCommandFinishedEvent(command, exitCode))
    }

    override fun promptStarted() {
      collectAndSendEvents(contentUpdate = null, otherEvent = TerminalPromptStartedEvent)
    }

    override fun promptFinished() {
      collectAndSendEvents(contentUpdate = null, otherEvent = TerminalPromptFinishedEvent)
    }
  })

  return outputFlow
}

private fun ensureEmulationActive(starter: TerminalStarterEx) {
  if (Thread.interrupted() || starter.isStopped) {
    throw CancellationException("Terminal emulation was stopped")
  }
}