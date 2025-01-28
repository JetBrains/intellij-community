package com.intellij.terminal.backend

import com.intellij.terminal.session.TerminalInputEvent
import com.intellij.terminal.session.TerminalOutputEvent
import com.intellij.terminal.session.TerminalSession
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow

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