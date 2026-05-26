package com.intellij.terminal.backend.hyperlinks

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinksSession
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksSessionId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Service(Service.Level.APP)
internal class TerminalHyperlinksSessionsManager(private val coroutineScope: CoroutineScope) {
  private val sessions = ConcurrentHashMap<TerminalHyperlinksSessionId, BackendTerminalHyperlinksSession>()
  private val sessionIdCounter = AtomicInteger(0)

  fun getSession(sessionId: TerminalHyperlinksSessionId): TerminalHyperlinksSession? {
    return sessions[sessionId]
  }

  fun createNewSession(project: Project): TerminalHyperlinksSession {
    TODO()
  }

  fun closeSession(sessionId: TerminalHyperlinksSessionId) {
    sessions[sessionId]?.coroutineScope?.cancel()
  }

  companion object {
    @JvmStatic
    fun getInstance(): TerminalHyperlinksSessionsManager = service()
  }
}