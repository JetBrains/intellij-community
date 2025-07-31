// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.frontend.hyperlinks.FrontendTerminalHyperlinkFacade
import com.intellij.terminal.session.*
import com.intellij.terminal.session.dto.toState
import com.intellij.terminal.session.dto.toTerminalState
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.DisposableWrapperList
import kotlinx.coroutines.*
import org.jetbrains.plugins.terminal.block.reworked.*
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor
import org.jetbrains.plugins.terminal.fus.*
import java.awt.Toolkit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

internal class TerminalSessionController(
  private val project: Project,
  private val sessionModel: TerminalSessionModel,
  private val outputModel: TerminalOutputModel,
  private val outputHyperlinkFacade: FrontendTerminalHyperlinkFacade?,
  private val alternateBufferModel: TerminalOutputModel,
  private val alternateBufferHyperlinkFacade: FrontendTerminalHyperlinkFacade?,
  private val blocksModel: TerminalBlocksModel,
  private val settings: JBTerminalSystemSettingsProviderBase,
  private val coroutineScope: CoroutineScope,
  private val fusActivity: FrontendOutputActivity,
  private val terminalAliasesStorage: TerminalAliasesStorage,
) {

  private val terminationListeners: DisposableWrapperList<Runnable> = DisposableWrapperList()
  private val shellIntegrationEventDispatcher: EventDispatcher<TerminalShellIntegrationEventsListener> =
    EventDispatcher.create(TerminalShellIntegrationEventsListener::class.java)

  private val edtContext = Dispatchers.EDT + ModalityState.any().asContextElement()

  private val documentUpdateLatencyReporter = BatchLatencyReporter(batchSize = 100) { samples ->
    ReworkedTerminalUsageCollector.logFrontendDocumentUpdateLatency(
      totalDuration = samples.totalDurationOf(DurationAndTextLength::duration),
      duration90 = samples.percentileOf(90, DurationAndTextLength::duration),
      thirdLargestDuration = samples.thirdLargestOf(DurationAndTextLength::duration),
      textLength90 = samples.percentileOf(90, DurationAndTextLength::textLength),
    )
  }

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
          outputHyperlinkFacade?.restoreFromState(event.outputHyperlinksState)
          alternateBufferHyperlinkFacade?.restoreFromState(event.alternateBufferHyperlinksState)
        }
      }
      is TerminalContentUpdatedEvent -> {
        fusActivity.eventReceived(event)
        updateOutputModel { model ->
          fusActivity.beforeModelUpdate()
          updateOutputModelContent(model, event)
          fusActivity.afterModelUpdate()
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

  private suspend fun updateOutputModel(block: (TerminalOutputModel) -> Unit) {
    withContext(edtContext) {
      val doUpdate = Runnable {
        block(getCurrentOutputModel())
      }
      val lookup = LookupManager.getInstance(project).activeLookup
      if (lookup != null && lookup.editor.isReworkedTerminalEditor) {
        lookup.performGuardedChange(doUpdate)
      } else {
        doUpdate.run()
      }
    }
  }

  @RequiresEdt
  private fun updateOutputModelContent(model: TerminalOutputModel, event: TerminalContentUpdatedEvent) {
    val startTime = TimeSource.Monotonic.markNow()

    model.updateContent(event)

    val latencyData = DurationAndTextLength(duration = startTime.elapsedNow(), textLength = event.text.length)
    documentUpdateLatencyReporter.update(latencyData)
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

private val LOG = logger<TerminalSessionController>()
