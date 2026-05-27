package com.intellij.terminal.backend.hyperlinks

import com.intellij.openapi.diagnostic.fileLogger
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksInputEvent
import org.jetbrains.plugins.terminal.hyperlinks.rpc.toUpdate
import org.jetbrains.plugins.terminal.session.impl.TerminalHyperlinksChangedEvent

internal fun scheduleHyperlinksSessionProcessing(session: BackendTerminalHyperlinksSession) {
  session.coroutineScope.launch(CoroutineName("BackendTerminalHyperlinksSession processing")) {
    processHyperlinksSession(session)
  }
}

private suspend fun processHyperlinksSession(session: BackendTerminalHyperlinksSession) = coroutineScope {
  launch(CoroutineName("Process input events")) {
    processInputEvents(session)
  }
  launch(CoroutineName("Process output hyperlink results")) {
    collectHyperlinkResults(session.outputHyperlinksFacade, session.hyperlinkUpdatesChannel)
  }
  launch(CoroutineName("Process altBuf hyperlink results")) {
    collectHyperlinkResults(session.alternateBufferHyperlinksFacade, session.hyperlinkUpdatesChannel)
  }
}

private suspend fun processInputEvents(session: BackendTerminalHyperlinksSession) {
  for (event in session.inputEventsSink) {
    try {
      processInputEvent(session, event)
    }
    catch (e: Exception) {
      LOG.error("Error when processing input event $event", e)
    }
  }
}

private fun processInputEvent(session: BackendTerminalHyperlinksSession, event: TerminalHyperlinksInputEvent) {
  when (event) {
    is TerminalHyperlinksInputEvent.ContentUpdated -> {
      val update = event.update.toUpdate()
      val facade = session.getFacade(event.isAlternateBuffer)
      facade.applyContentUpdate(update)
    }
  }
}

private suspend fun collectHyperlinkResults(facade: BackendTerminalHyperlinkFacade, sink: SendChannel<TerminalHyperlinksChangedEvent>) {
  facade.heartbeatFlow.collect {
    val changeEvent = try {
      facade.collectResultsAndMaybeStartNewTask()
    }
    catch (e: Exception) {
      LOG.error("Error when collecting hyperlink results", e)
      return@collect
    }

    if (changeEvent != null) {
      try {
        facade.updateModelState(changeEvent)
      }
      catch (e: Exception) {
        LOG.error("Error when updating model state: $changeEvent", e)
      }
    }

    if (changeEvent != null) {
      sink.send(changeEvent)
    }
  }
}

private fun BackendTerminalHyperlinksSession.getFacade(isAlternateBuffer: Boolean): BackendTerminalHyperlinkFacade {
  return if (isAlternateBuffer) alternateBufferHyperlinksFacade else outputHyperlinksFacade
}

private val LOG = fileLogger()