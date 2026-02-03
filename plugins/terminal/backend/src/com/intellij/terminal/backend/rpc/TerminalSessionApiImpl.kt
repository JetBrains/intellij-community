package com.intellij.terminal.backend.rpc

import com.intellij.terminal.backend.TerminalSessionsManager
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionApi
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId
import org.jetbrains.plugins.terminal.session.impl.TerminalInputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession

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

  override suspend fun hasRunningCommands(sessionId: TerminalSessionId): Boolean {
    return getSession(sessionId).hasRunningCommands()
  }

  private fun getSession(sessionId: TerminalSessionId): TerminalSession {
    return TerminalSessionsManager.getInstance().getSession(sessionId)
           ?: error("Failed to find TerminalSession with ID: $sessionId")
  }
}