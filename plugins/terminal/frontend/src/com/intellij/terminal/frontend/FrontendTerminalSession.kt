package com.intellij.terminal.frontend

import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalInputEvent
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalOutputEvent
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalSession
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionApi
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId

internal class FrontendTerminalSession(private val id: TerminalSessionId) : TerminalSession {
  override suspend fun sendInputEvent(event: TerminalInputEvent) {
    TerminalSessionApi.getInstance().sendInputEvent(id, event)
  }

  override suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>> {
    return TerminalSessionApi.getInstance().getOutputFlow(id)
  }
}