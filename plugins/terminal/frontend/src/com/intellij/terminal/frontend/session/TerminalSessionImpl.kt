package com.intellij.terminal.frontend.session

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.jediterm.terminal.TtyConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.LocalTerminalTtyConnector
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.original
import org.jetbrains.plugins.terminal.session.impl.TerminalInputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalSessionTerminatedEvent

internal class TerminalSessionImpl(
  private val inputChannel: SendChannel<TerminalInputEvent>,
  outputFlow: Flow<List<TerminalOutputEvent>>,
  override val coroutineScope: CoroutineScope,
  private val ttyConnector: TtyConnector,
) : TerminalSession {
  @Volatile
  override var isClosed: Boolean = false
    private set

  private val closeAwareOutputFlow = outputFlow.onEach { events ->
    if (events.any { it == TerminalSessionTerminatedEvent }) {
      isClosed = true
    }
  }

  private var missingLocalTtyConnectorLogged = false

  private val localTtyConnector: LocalTerminalTtyConnector?
    /**
     * In production, [ttyConnector] is always a [LocalTerminalTtyConnector]; on a miss, [eelDescriptor]
     * and [processId] return guesses, hence `LOG.error`.
     * In tests with a fake connector (see `LoopbackTtyConnector`), `LOG.error` fails
     * tests that rely on these getters.
     */
    get() = ttyConnector.original as? LocalTerminalTtyConnector ?: run {
      if (!missingLocalTtyConnectorLogged) {
        missingLocalTtyConnectorLogged = true
        LOG.error("Unable to find LocalTerminalTtyConnector in $ttyConnector")
      }
      null
    }

  override val eelDescriptor: EelDescriptor
    get() = localTtyConnector?.eelDescriptor ?: LocalEelDescriptor

  override val processId: Long
    get() {
      val localTtyConnector = localTtyConnector ?: return -1
      return localTtyConnector.shellEelProcess.eelProcess.pid.value
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

  override suspend fun hasRunningCommands(): Boolean {
    if (isClosed) return false
    return withContext(Dispatchers.IO) {
      TerminalUtil.hasRunningCommands(ttyConnector)
    }
  }
}

private val LOG = logger<TerminalSessionImpl>()
