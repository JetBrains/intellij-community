package com.intellij.terminal.backend

import com.intellij.terminal.session.TerminalInputEvent
import com.intellij.terminal.session.TerminalOutputEvent
import com.intellij.terminal.session.TerminalSessionTerminatedEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onEach

internal class BackendTerminalSessionImpl(
  private val inputChannel: SendChannel<TerminalInputEvent>,
  outputFlow: Flow<List<TerminalOutputEvent>>,
  override val coroutineScope: CoroutineScope,
) : BackendTerminalSession {
  @Volatile
  override var isClosed: Boolean = false
    private set

  private val closeAwareOutputFlow = outputFlow.onEach { events ->
    if (events.any { it == TerminalSessionTerminatedEvent }) {
      isClosed = true
    }
  }

  override suspend fun getInputChannel(): SendChannel<TerminalInputEvent> {
    if (isClosed) {
      return Channel<TerminalInputEvent>(capacity = 0).also { it.close() }
    }

    return inputChannel
  }

  override suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>> {
    if (isClosed) {
      return emptyFlow()
    }

    return closeAwareOutputFlow
  }
}