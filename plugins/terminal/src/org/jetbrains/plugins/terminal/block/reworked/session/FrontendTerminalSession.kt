// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session

import fleet.rpc.client.durable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onEach
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionApi
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId
import org.jetbrains.plugins.terminal.session.TerminalInputEvent
import org.jetbrains.plugins.terminal.session.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.TerminalSession
import org.jetbrains.plugins.terminal.session.TerminalSessionTerminatedEvent

/**
 * [TerminalSession] implementation that is delegating the methods to [TerminalSessionApi] RPC to backend.
 *
 * Normally, it should be located in the frontend module, but it can't be moved there
 * because it should be accessible from the shared terminal widget creating API with a lot of external usages.
 */
@ApiStatus.Internal
class FrontendTerminalSession(val id: TerminalSessionId) : TerminalSession {
  @Volatile
  override var isClosed: Boolean = false
    private set

  override suspend fun getInputChannel(): SendChannel<TerminalInputEvent> {
    if (isClosed) {
      return Channel<TerminalInputEvent>(capacity = 0).also { it.close() }
    }
    return durable {
      TerminalSessionApi.getInstance().getInputChannel(id)
    }
  }

  override suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>> {
    if (isClosed) {
      return emptyFlow()
    }

    return durable {
      TerminalSessionApi.getInstance().getOutputFlow(id)
        .onEach { events ->
          if (events.any { it == TerminalSessionTerminatedEvent }) {
            isClosed = true
          }
        }
    }
  }

  override suspend fun hasRunningCommands(): Boolean {
    if (isClosed) return false
    return durable {
      TerminalSessionApi.getInstance().hasRunningCommands(id)
    }
  }
}