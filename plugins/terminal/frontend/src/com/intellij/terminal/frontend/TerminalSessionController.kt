// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.session.*
import com.intellij.terminal.session.dto.toState
import com.intellij.terminal.session.dto.toStyleRange
import com.intellij.terminal.session.dto.toTerminalState
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.DisposableWrapperList
import kotlinx.coroutines.*
import org.jetbrains.plugins.terminal.block.reworked.TerminalBlocksModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalShellIntegrationEventsListener
import java.awt.Toolkit
import kotlin.coroutines.cancellation.CancellationException

internal class TerminalSessionController(
  private val sessionModel: TerminalSessionModel,
  private val outputModel: TerminalOutputModel,
  private val alternateBufferModel: TerminalOutputModel,
  private val blocksModel: TerminalBlocksModel,
  private val settings: JBTerminalSystemSettingsProviderBase,
  private val coroutineScope: CoroutineScope,
) {

  private val terminationListeners: DisposableWrapperList<Runnable> = DisposableWrapperList()
  private val shellIntegrationEventDispatcher: EventDispatcher<TerminalShellIntegrationEventsListener> =
    EventDispatcher.create(TerminalShellIntegrationEventsListener::class.java)

  private val edtContext = Dispatchers.EDT + ModalityState.any().asContextElement()

  fun handleEvents(session: TerminalSession) {
    coroutineScope.launch {
      val outputFlow = session.getOutputFlow()
      outputFlow.collect { events ->
        doHandleEvents(events)
      }
    }
  }

  private suspend fun doHandleEvents(events: List<TerminalOutputEvent>) {
    for (event in events) {
      try {
        handleEvent(event)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (t: Throwable) {
        thisLogger().error(t)
      }
    }
  }

  private suspend fun handleEvent(event: TerminalOutputEvent) {
    when (event) {
      is TerminalInitialStateEvent -> {
        sessionModel.updateTerminalState(event.sessionState.toTerminalState())
        updateOutputModel {
          outputModel.restoreFromState(event.outputModelState.toState())
          alternateBufferModel.restoreFromState(event.alternateBufferState.toState())
          blocksModel.restoreFromState(event.blocksModelState.toState())
        }
      }
      is TerminalContentUpdatedEvent -> {
        updateOutputModel { model ->
          val styles = event.styles.map { it.toStyleRange() }
          model.updateContent(event.startLineLogicalIndex, event.text, styles)
        }
      }
      is TerminalCursorPositionChangedEvent -> {
        updateOutputModel { model ->
          model.updateCursorPosition(event.logicalLineIndex, event.columnIndex)
        }
      }
      is TerminalStateChangedEvent -> {
        val state = event.state.toTerminalState()
        sessionModel.updateTerminalState(state)
      }
      is TerminalBeepEvent -> {
        if (settings.audibleBell()) {
          Toolkit.getDefaultToolkit().beep()
        }
      }
      TerminalSessionTerminatedEvent -> {
        fireSessionTerminated()
      }
      TerminalPromptStartedEvent -> {
        withContext(edtContext) {
          blocksModel.promptStarted(outputModel.cursorOffsetState.value)
        }
        shellIntegrationEventDispatcher.multicaster.promptStarted()
      }
      TerminalPromptFinishedEvent -> {
        withContext(edtContext) {
          blocksModel.promptFinished(outputModel.cursorOffsetState.value)
        }
        shellIntegrationEventDispatcher.multicaster.promptFinished()
      }
      is TerminalCommandStartedEvent -> {
        withContext(edtContext) {
          blocksModel.commandStarted(outputModel.cursorOffsetState.value)
        }
        shellIntegrationEventDispatcher.multicaster.commandStarted(event.command)
      }
      is TerminalCommandFinishedEvent -> {
        withContext(edtContext) {
          blocksModel.commandFinished(event.exitCode)
        }
        shellIntegrationEventDispatcher.multicaster.commandFinished(event.command, event.exitCode)
      }
    }
  }

  private suspend fun updateOutputModel(block: (TerminalOutputModel) -> Unit) {
    withContext(edtContext) {
      block(getCurrentOutputModel())
    }
  }

  private fun getCurrentOutputModel(): TerminalOutputModel {
    return if (sessionModel.terminalState.value.isAlternateScreenBuffer) alternateBufferModel else outputModel
  }

  fun addTerminationCallback(onTerminated: Runnable, parentDisposable: Disposable) {
    terminationListeners.add(onTerminated, parentDisposable)
  }

  private fun fireSessionTerminated() {
    for (listener in terminationListeners) {
      try {
        listener.run()
      }
      catch (t: Throwable) {
        thisLogger().error("Unhandled exception in termination listener", t)
      }
    }
  }

  fun addShellIntegrationListener(parentDisposable: Disposable, listener: TerminalShellIntegrationEventsListener) {
    shellIntegrationEventDispatcher.addListener(listener, parentDisposable)
  }
}