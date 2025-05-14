package com.intellij.terminal.backend.rpc

import com.intellij.terminal.backend.TerminalSessionsManager
import com.intellij.terminal.session.TerminalInputEvent
import com.intellij.terminal.session.TerminalOutputEvent
import com.intellij.terminal.session.TerminalSession
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionApi
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId

internal class TerminalSessionApiImpl : TerminalSessionApi {
  override suspend fun getInputChannel(sessionId: TerminalSessionId): SendChannel<TerminalInputEvent> {
    val channelsManager = TerminalInputChannelsManager.getInstanceOrNull()
    // Get the channel from the manager if it is available, otherwise get it directly from the session.
    return channelsManager?.getInputChannel(sessionId)
           ?: getSession(sessionId).getInputChannel()
  }

  override suspend fun getOutputFlow(sessionId: TerminalSessionId): Flow<List<TerminalOutputEvent>> {
    return getSession(sessionId).getOutputFlow()
  }

  private fun getSession(sessionId: TerminalSessionId): TerminalSession {
    return TerminalSessionsManager.getInstance().getSession(sessionId)
           ?: error("Failed to find TerminalSession with ID: $sessionId")
  }
}