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

  override val eelDescriptor: EelDescriptor
    /**
     * Falls back to [LocalEelDescriptor] when [ttyConnector] isn't a [LocalTerminalTtyConnector].
     * In production, [ttyConnector] is always a [LocalTerminalTtyConnector].
     * The fallback is test-only, exercised by tests driving the session through a fake connector.
     */
    get() = (ttyConnector.original as? LocalTerminalTtyConnector)?.eelDescriptor ?: LocalEelDescriptor

  private var missingLocalTtyConnectorLogged = false

  override val processId: Long
    /**
     * In production, [ttyConnector] is always a [LocalTerminalTtyConnector].
     * Miss can happen only in tests where a fake connector is used, so `LOG.error` to fail the test.
     */
    get() {
      val localTtyConnector = ttyConnector.original as? LocalTerminalTtyConnector
      if (localTtyConnector == null) {
        if (!missingLocalTtyConnectorLogged) {
          missingLocalTtyConnectorLogged = true
          LOG.error("Unable to find LocalTerminalTtyConnector in $ttyConnector")
        }
        return -1
      }
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
