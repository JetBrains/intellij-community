// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session

import com.intellij.terminal.session.TerminalInputEvent
import com.intellij.terminal.session.TerminalOutputEvent
import com.intellij.terminal.session.TerminalSession
import com.intellij.terminal.session.TerminalSessionTerminatedEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onEach
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionApi
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId

/**
 * [TerminalSession] implementation that is delegating the methods to [TerminalSessionApi] RPC to backend.
 *
 * Normally, it should be located in the frontend module, but it can't be moved there
 * because it should be accessible from the shared terminal widget creating API with a lot of external usages.
 */
internal class FrontendTerminalSession(private val id: TerminalSessionId) : TerminalSession {
  @Volatile
  private var isClosed: Boolean = false

  override suspend fun sendInputEvent(event: TerminalInputEvent) {
    if (isClosed) {
      return
    }

    TerminalSessionApi.getInstance().sendInputEvent(id, event)
  }

  override suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>> {
    if (isClosed) {
      return emptyFlow()
    }

    val flow = TerminalSessionApi.getInstance().getOutputFlow(id)
    return flow.onEach { events ->
      if (events.any { it == TerminalSessionTerminatedEvent }) {
        isClosed = true
      }
    }
  }
}