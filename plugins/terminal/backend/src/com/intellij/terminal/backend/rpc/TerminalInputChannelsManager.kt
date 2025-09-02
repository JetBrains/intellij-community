package com.intellij.terminal.backend.rpc

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.components.serviceOrNull
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.backend.TerminalSessionsManager
import com.intellij.terminal.session.TerminalInputEvent
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId

/**
 * Service that has separate instances for each [com.intellij.openapi.client.ClientAppSession]
 * with [com.intellij.openapi.client.ClientKind.REMOTE].
 * Every instance stores the copy of the original input channel for each [com.intellij.terminal.backend.BackendTerminalSession].
 * This copy is needed to pass it to the RPC logic and avoid closing of the original channel on the client disconnect.
 *
 * Once the client disconnects, the instance of this service will be disposed, releasing the stored channels.
 */
@OptIn(AwaitCancellationAndInvoke::class)
internal class TerminalInputChannelsManager(private val coroutineScope: CoroutineScope) {
  private val channels = mutableMapOf<TerminalSessionId, SendChannel<TerminalInputEvent>>()
  private val lock = Mutex()

  suspend fun getInputChannel(sessionId: TerminalSessionId): SendChannel<TerminalInputEvent>? {
    val session = TerminalSessionsManager.getInstance().getSession(sessionId) ?: return null

    return lock.withLock {
      channels[sessionId]?.let { return@withLock it }

      val originalChannel = session.getInputChannel()
      // Use the existing session scope to remove the created channel once the session is terminated
      val scope = session.coroutineScope.childScope("${ClientId.current} input forwarding")
      createClientScopedChannel(sessionId, originalChannel, scope)
    }
  }

  private fun createClientScopedChannel(
    sessionId: TerminalSessionId,
    originalChannel: SendChannel<TerminalInputEvent>,
    scope: CoroutineScope,
  ): SendChannel<TerminalInputEvent> {
    val clientScopedChannel = Channel<TerminalInputEvent>(capacity = Channel.UNLIMITED)
    // Forward events from the new channel to the original one
    scope.launch {
      for (event in clientScopedChannel) {
        originalChannel.send(event)
      }
    }

    channels[sessionId] = clientScopedChannel

    // Cancel the scope when the client disconnects (this service will be disposed on client disconnect)
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      scope.cancel()
    }

    scope.awaitCancellationAndInvoke {
      lock.withLock {
        channels.remove(sessionId)
      }
    }

    return clientScopedChannel
  }

  companion object {
    /**
     * Returns not null value when current [ClientId] has [com.intellij.openapi.client.ClientKind.REMOTE].
     * Otherwise, returns null, for example, in the monolith case.
     */
    fun getInstanceOrNull(): TerminalInputChannelsManager? = serviceOrNull()
  }
}