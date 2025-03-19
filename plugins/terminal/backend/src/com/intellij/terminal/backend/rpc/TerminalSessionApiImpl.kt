package com.intellij.terminal.backend.rpc

import com.intellij.platform.kernel.backend.findValueEntity
import com.intellij.terminal.session.TerminalInputEvent
import com.intellij.terminal.session.TerminalOutputEvent
import com.intellij.terminal.session.TerminalSession
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionApi
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId

internal class TerminalSessionApiImpl : TerminalSessionApi {
  override suspend fun getInputChannel(sessionId: TerminalSessionId): SendChannel<TerminalInputEvent> {
    return getSession(sessionId).getInputChannel()
  }

  override suspend fun getOutputFlow(sessionId: TerminalSessionId): Flow<List<TerminalOutputEvent>> {
    return getSession(sessionId).getOutputFlow()
  }

  private suspend fun getSession(sessionId: TerminalSessionId): TerminalSession {
    return sessionId.eid.findValueEntity<TerminalSession>()?.value
           ?: error("Failed to find TerminalSession with ID: ${sessionId.eid}")
  }
}