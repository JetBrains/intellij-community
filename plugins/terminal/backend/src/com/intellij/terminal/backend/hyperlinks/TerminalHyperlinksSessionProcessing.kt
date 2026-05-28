package com.intellij.terminal.backend.hyperlinks

import com.intellij.openapi.diagnostic.fileLogger
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinksOutputEvent
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksInputEvent
import org.jetbrains.plugins.terminal.hyperlinks.rpc.toUpdate

internal fun scheduleHyperlinksSessionProcessing(session: BackendTerminalHyperlinksSession) {
  session.coroutineScope.launch(CoroutineName("BackendTerminalHyperlinksSession processing")) {
    processHyperlinksSession(session)
  }
}

private suspend fun processHyperlinksSession(session: BackendTerminalHyperlinksSession) = coroutineScope {
  launch(CoroutineName("Process input events")) {
    processInputEvents(session.hyperlinksFacade, session.inputEventsSink)
  }
  launch(CoroutineName("Process results")) {
    collectHyperlinkResults(session.hyperlinksFacade, session.hyperlinkUpdatesChannel)
  }
}

private suspend fun processInputEvents(
  hyperlinkFacade: BackendTerminalHyperlinkFacade,
  inputEventsChannel: ReceiveChannel<TerminalHyperlinksInputEvent>,
) {
  for (event in inputEventsChannel) {
    try {
      processInputEvent(hyperlinkFacade, event)
    }
    catch (e: Exception) {
      LOG.error("Error when processing input event $event", e)
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
  }
}

private suspend fun collectHyperlinkResults(facade: BackendTerminalHyperlinkFacade, sink: SendChannel<TerminalHyperlinksOutputEvent>) {
  facade.heartbeatFlow.collect {
    val event = try {
      facade.collectResultsAndMaybeStartNewTask()
    }
    catch (e: Exception) {
      LOG.error("Error when collecting hyperlink results", e)
      return@collect
    }

    if (event is TerminalHyperlinksOutputEvent.HyperlinksUpdated) {
      try {
        facade.updateModelState(event)
      }
      catch (e: Exception) {
        LOG.error("Error when updating model state: $event", e)
      }
    }

    if (event != null) {
      sink.send(event)
    }
  }
}

private val LOG = fileLogger()