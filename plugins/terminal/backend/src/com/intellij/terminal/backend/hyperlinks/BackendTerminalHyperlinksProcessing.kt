package com.intellij.terminal.backend.hyperlinks

import com.intellij.openapi.diagnostic.fileLogger
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksInputEvent
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksOutputEvent
import org.jetbrains.plugins.terminal.hyperlinks.session.toUpdate

internal fun scheduleHyperlinksSessionProcessing(session: BackendTerminalHyperlinksSession) {
  session.coroutineScope.launch(CoroutineName("BackendTerminalHyperlinksSession processing")) {
    processHyperlinksSession(session)
  }
}

private suspend fun processHyperlinksSession(session: BackendTerminalHyperlinksSession) = coroutineScope {
  // BackendTerminalHyperlinkFacade is not thread-safe, so we need to synchronize access to it
  val facadeMutex = Mutex()

  launch(CoroutineName("Process input events")) {
    processInputEvents(session.hyperlinksFacade, session.inputEventsSink, facadeMutex)
  }
  launch(CoroutineName("Process results")) {
    collectHyperlinkResults(session.hyperlinksFacade, session.hyperlinkUpdatesChannel, facadeMutex)
  }
}

private suspend fun processInputEvents(
  hyperlinkFacade: BackendTerminalHyperlinkFacade,
  inputEventsChannel: ReceiveChannel<TerminalHyperlinksInputEvent>,
  facadeMutex: Mutex,
) {
  for (event in inputEventsChannel) {
    facadeMutex.withLock {
      try {
        processInputEvent(hyperlinkFacade, event)
      }
      catch (e: Exception) {
        LOG.error("Error when processing input event $event", e)
      }
    }
  }
}

private fun processInputEvent(
  hyperlinkFacade: BackendTerminalHyperlinkFacade,
  event: TerminalHyperlinksInputEvent,
) {
  when (event) {
    is TerminalHyperlinksInputEvent.ContentUpdated -> {
      val update = event.update.toUpdate()
      hyperlinkFacade.applyContentUpdate(update)
    }
    is TerminalHyperlinksInputEvent.WorkingDirectoryChanged -> {
      hyperlinkFacade.updateWorkingDirectory(event.workingDirectory)
    }
  }
}

private suspend fun collectHyperlinkResults(
  facade: BackendTerminalHyperlinkFacade,
  sink: SendChannel<TerminalHyperlinksOutputEvent>,
  facadeMutex: Mutex,
) = coroutineScope {
  launch(CoroutineName("Heartbeat")) {
    facade.heartbeatFlow.collect {
      val events = facadeMutex.withLock {
        collectResultsAndApplyToModel(facade)
      }
      for (event in events) {
        sink.send(event)
      }
    }
  }

  launch(CoroutineName("Filter updates")) {
    facade.filterUpdatesFlow.collect {
      sink.send(TerminalHyperlinksOutputEvent.FiltersUpdated)
    }
  }
}

private fun collectResultsAndApplyToModel(facade: BackendTerminalHyperlinkFacade): List<TerminalHyperlinksOutputEvent> {
  val events = try {
    facade.collectResultsAndMaybeStartNewTask()
  }
  catch (e: Exception) {
    LOG.error("Error when collecting hyperlink results", e)
    return emptyList()
  }

  for (event in events) {
    if (event is TerminalHyperlinksOutputEvent.HyperlinksUpdated) {
      try {
        facade.updateModelState(event)
      }
      catch (e: Exception) {
        LOG.error("Error when updating model state: $event", e)
      }
    }
  }

  return events
}

private val LOG = fileLogger()