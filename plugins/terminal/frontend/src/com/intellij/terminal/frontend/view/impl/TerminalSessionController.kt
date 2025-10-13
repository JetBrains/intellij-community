// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.frontend.view.hyperlinks.FrontendTerminalHyperlinkFacade
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.DisposableWrapperList
import kotlinx.coroutines.*
import org.jetbrains.plugins.terminal.block.reworked.TerminalAliasesStorage
import org.jetbrains.plugins.terminal.block.reworked.TerminalBlocksModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalShellIntegrationEventsListener
import org.jetbrains.plugins.terminal.session.*
import org.jetbrains.plugins.terminal.session.dto.toState
import org.jetbrains.plugins.terminal.session.dto.toTerminalState
import java.awt.Toolkit
import kotlin.coroutines.cancellation.CancellationException

internal class TerminalSessionController(
  private val sessionModel: TerminalSessionModel,
  private val outputModelController: TerminalOutputModelController,
  private val outputHyperlinkFacade: FrontendTerminalHyperlinkFacade?,
  private val alternateBufferModelController: TerminalOutputModelController,
  private val alternateBufferHyperlinkFacade: FrontendTerminalHyperlinkFacade?,
  private val blocksModel: TerminalBlocksModel,
  private val settings: JBTerminalSystemSettingsProviderBase,
  private val coroutineScope: CoroutineScope,
  private val terminalAliasesStorage: TerminalAliasesStorage,
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
        withContext(edtContext) {
          outputModelController.applyPendingUpdates()
          alternateBufferModelController.applyPendingUpdates()

          outputModelController.model.restoreFromState(event.outputModelState.toState())
          alternateBufferModelController.model.restoreFromState(event.alternateBufferState.toState())
          blocksModel.restoreFromState(event.blocksModelState.toState())
          outputHyperlinkFacade?.restoreFromState(event.outputHyperlinksState)
          alternateBufferHyperlinkFacade?.restoreFromState(event.alternateBufferHyperlinksState)
        }
      }
      is TerminalContentUpdatedEvent -> {
        updateOutputModel { controller ->
          controller.updateContent(event)
        }
      }
      is TerminalCursorPositionChangedEvent -> {
        updateOutputModel { controller ->
          controller.updateCursorPosition(event)
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
          outputModelController.applyPendingUpdates()
          blocksModel.promptStarted(outputModelController.model.cursorOffset)
        }
        shellIntegrationEventDispatcher.multicaster.promptStarted()
      }
      TerminalPromptFinishedEvent -> {
        withContext(edtContext) {
          outputModelController.applyPendingUpdates()
          blocksModel.promptFinished(outputModelController.model.cursorOffset)
        }
        shellIntegrationEventDispatcher.multicaster.promptFinished()
      }
      is TerminalCommandStartedEvent -> {
        withContext(edtContext) {
          outputModelController.applyPendingUpdates()
          blocksModel.commandStarted(outputModelController.model.cursorOffset)
        }
        shellIntegrationEventDispatcher.multicaster.commandStarted(event.command)
      }
      is TerminalCommandFinishedEvent -> {
        withContext(edtContext) {
          outputModelController.applyPendingUpdates()
          blocksModel.commandFinished(event.exitCode)
        }
        shellIntegrationEventDispatcher.multicaster.commandFinished(event.command, event.exitCode, event.currentDirectory)
      }
      is TerminalAliasesReceivedEvent -> {
        terminalAliasesStorage.setAliasesInfo(event.aliases)
      }
      is TerminalHyperlinksHeartbeatEvent -> {
        LOG.warn("TerminalHyperlinksHeartbeatEvent isn't supposed to reach the frontend")
      }
      is TerminalHyperlinksChangedEvent -> {
        withContext(edtContext) {
          getCurrentHyperlinkFacade(event)?.updateHyperlinks(event)
        }
      }
    }
  }

  private fun getCurrentHyperlinkFacade(event: TerminalHyperlinksChangedEvent): FrontendTerminalHyperlinkFacade? {
    return if (event.isInAlternateBuffer) alternateBufferHyperlinkFacade else outputHyperlinkFacade
  }

  private suspend fun updateOutputModel(block: (TerminalOutputModelController) -> Unit) {
    withContext(edtContext) {
      val controller = getCurrentOutputModelController()
      block(controller)
    }
  }

  private fun getCurrentOutputModelController(): TerminalOutputModelController {
    return if (sessionModel.terminalState.value.isAlternateScreenBuffer) {
      alternateBufferModelController
    }
    else outputModelController
  }

  fun addTerminationCallback(parentDisposable: Disposable, onTerminated: Runnable) {
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

private val LOG = logger<TerminalSessionController>()
