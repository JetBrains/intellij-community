package com.intellij.terminal.backend

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalInputEvent
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalOutputEvent
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalSession

class BackendTerminalSession(
  private val inputChannel: SendChannel<TerminalInputEvent>,
  private val outputFlow: Flow<List<TerminalOutputEvent>>,
) : TerminalSession {
  override suspend fun sendInputEvent(event: TerminalInputEvent) {
    inputChannel.send(event)
  }

  override suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>> {
    return outputFlow
  }
}