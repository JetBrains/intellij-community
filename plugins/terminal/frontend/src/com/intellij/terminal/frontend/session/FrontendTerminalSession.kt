package com.intellij.terminal.frontend.session

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onEach
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId
import org.jetbrains.plugins.terminal.session.impl.TerminalInputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalSessionTerminatedEvent

@ApiStatus.Internal
class FrontendTerminalSession(val id: TerminalSessionId) : TerminalSession {
  @Volatile
  override var isClosed: Boolean = false
    private set

  override suspend fun getInputChannel(): SendChannel<TerminalInputEvent> {
    if (isClosed) {
      return Channel<TerminalInputEvent>(capacity = 0).also { it.close() }
    }
    return getSession().getInputChannel()
  }

  override suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>> {
    if (isClosed) {
      return emptyFlow()
    }

    return getSession().getOutputFlow().onEach { events ->
      if (events.any { it == TerminalSessionTerminatedEvent }) {
        isClosed = true
      }
    }
  }

  override suspend fun hasRunningCommands(): Boolean {
    if (isClosed) return false
    return getSession().hasRunningCommands()
  }

  private fun getSession(): TerminalSession {
    return TerminalSessionsManager.getInstance().getSession(id)
           ?: error("Failed to find TerminalSession with ID: $id")
  }
}