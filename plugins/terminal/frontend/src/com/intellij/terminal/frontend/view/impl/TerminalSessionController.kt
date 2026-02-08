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
import com.intellij.util.containers.DisposableWrapperList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.session.TerminalStartupOptions
import org.jetbrains.plugins.terminal.session.impl.TerminalBeepEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCursorPositionChangedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalHyperlinksChangedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalHyperlinksHeartbeatEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalInitialStateEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalSessionTerminatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalStateChangedEvent
import org.jetbrains.plugins.terminal.session.impl.dto.toOptions
import org.jetbrains.plugins.terminal.session.impl.dto.toState
import org.jetbrains.plugins.terminal.session.impl.dto.toTerminalState
import java.awt.Toolkit
import kotlin.coroutines.cancellation.CancellationException

internal class TerminalSessionController(
  private val sessionModel: TerminalSessionModel,
  private val outputModelController: TerminalOutputModelController,
  private val outputHyperlinkFacade: FrontendTerminalHyperlinkFacade?,
  private val alternateBufferModelController: TerminalOutputModelController,
  private val alternateBufferHyperlinkFacade: FrontendTerminalHyperlinkFacade?,
  private val startupOptionsDeferred: CompletableDeferred<TerminalStartupOptions>,
  private val settings: JBTerminalSystemSettingsProviderBase,
  private val coroutineScope: CoroutineScope,
) {
  private val eventHandlers: DisposableWrapperList<TerminalOutputEventsHandler> = DisposableWrapperList()
  private val terminationListeners: DisposableWrapperList<Runnable> = DisposableWrapperList()

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
    invokeBaseHandler(event)

    for (handler in eventHandlers) {
      handler.handleEvent(event)
    }
  }

  private suspend fun invokeBaseHandler(event: TerminalOutputEvent) {
    when (event) {
      is TerminalInitialStateEvent -> {
        sessionModel.updateTerminalState(event.sessionState.toTerminalState())
        startupOptionsDeferred.complete(event.startupOptions.toOptions())
        withContext(edtContext) {
          outputModelController.applyPendingUpdates()
          alternateBufferModelController.applyPendingUpdates()

          outputModelController.model.restoreFromState(event.outputModelState.toState())
          alternateBufferModelController.model.restoreFromState(event.alternateBufferState.toState())
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
      is TerminalHyperlinksHeartbeatEvent -> {
        LOG.warn("TerminalHyperlinksHeartbeatEvent isn't supposed to reach the frontend")
      }
      is TerminalHyperlinksChangedEvent -> {
        withContext(edtContext) {
          getCurrentHyperlinkFacade(event)?.updateHyperlinks(event)
        }
      }
      else -> {
        // do nothing
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

  fun addEventsHandler(handler: TerminalOutputEventsHandler, parentDisposable: Disposable? = null) {
    if (parentDisposable != null) {
      eventHandlers.add(handler, parentDisposable)
    }
    else eventHandlers.add(handler)
  }
}

private val LOG = logger<TerminalSessionController>()
