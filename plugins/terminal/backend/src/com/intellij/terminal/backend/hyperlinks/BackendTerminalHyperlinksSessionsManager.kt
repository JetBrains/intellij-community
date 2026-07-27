package com.intellij.terminal.backend.hyperlinks

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksInputEvent
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksOutputEvent
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksSessionId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Service(Service.Level.PROJECT)
internal class BackendTerminalHyperlinksSessionsManager(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val sessions = ConcurrentHashMap<TerminalHyperlinksSessionId, BackendTerminalHyperlinksSession>()
  private val sessionIdCounter = AtomicInteger(0)

  fun getSession(sessionId: TerminalHyperlinksSessionId): BackendTerminalHyperlinksSession? {
    return sessions[sessionId]
  }

  fun closeSession(sessionId: TerminalHyperlinksSessionId) {
    sessions[sessionId]?.coroutineScope?.cancel()
  }

  /**
   * @param eelDescriptor environment where the terminal process is running.
   */
  fun createNewSession(eelDescriptor: EelDescriptor): BackendTerminalHyperlinksSession {
    val newId = TerminalHyperlinksSessionId(sessionIdCounter.getAndIncrement())

    val sessionScope = coroutineScope.childScope("BackendTerminalHyperlinksSession#$newId")
    sessionScope.coroutineContext.job.invokeOnCompletion {
      sessions.remove(newId)
    }

    val session = startHyperlinksSession(project, eelDescriptor, newId, sessionScope)
    sessions[newId] = session
    return session
  }

  private fun startHyperlinksSession(
    project: Project,
    eelDescriptor: EelDescriptor,
    id: TerminalHyperlinksSessionId,
    scope: CoroutineScope,
  ): BackendTerminalHyperlinksSession {
    val hyperlinksFacade = BackendTerminalHyperlinkFacade(
      debugName = "Backend#${id.id}",
      project = project,
      eelDescriptor = eelDescriptor,
      coroutineScope = scope.childScope("BackendTerminalHyperlinkFacade"),
    )

    val inputEventsSink = Channel<TerminalHyperlinksInputEvent>()
    val hyperlinkUpdatesChannel = Channel<TerminalHyperlinksOutputEvent>()

    val session = BackendTerminalHyperlinksSession(
      id = id,
      inputEventsSink = inputEventsSink,
      hyperlinkUpdatesChannel = hyperlinkUpdatesChannel,
      hyperlinksFacade = hyperlinksFacade,
      coroutineScope = scope,
    )

    scheduleHyperlinksSessionProcessing(session)

    return session
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): BackendTerminalHyperlinksSessionsManager = project.service()
  }
}