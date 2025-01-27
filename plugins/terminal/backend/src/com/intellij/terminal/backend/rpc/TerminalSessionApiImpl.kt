package com.intellij.terminal.backend.rpc

import com.intellij.platform.kernel.backend.findValueEntity
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalInputEvent
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalOutputEvent
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalSession
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionApi
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId

internal class TerminalSessionApiImpl : TerminalSessionApi {
  override suspend fun sendInputEvent(sessionId: TerminalSessionId, event: TerminalInputEvent) {
    getSession(sessionId).sendInputEvent(event)
  }

  override suspend fun getOutputFlow(sessionId: TerminalSessionId): Flow<List<TerminalOutputEvent>> {
    return getSession(sessionId).getOutputFlow()
  }

  private suspend fun getSession(sessionId: TerminalSessionId): TerminalSession {
    return sessionId.eid.findValueEntity<TerminalSession>()?.value
           ?: error("Failed to find TerminalSession with ID: ${sessionId.eid}")
  }
}