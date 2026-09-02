// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.impl.util.network

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.registry.Registry
import io.modelcontextprotocol.kotlin.sdk.server.StreamableHttpServerTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val logger = fileLogger()

internal const val STREAMABLE_SESSION_IDLE_TIMEOUT_REGISTRY_KEY: String = "mcp.server.streamable.session.idle.timeout.ms"

private const val ABANDONED_SESSION_CLOSED = "Closing an abandoned Streamable HTTP session"

/** A test asserts on this text, so a reword changes an observable. */
internal const val UNINITIALIZED_SESSION_CLOSED: String = "$ABANDONED_SESSION_CLOSED that never initialized"

/** Inactivity is the only signal an abandoned session gives. A client need not send DELETE, and the SDK clients do not. */
private val streamableSessionIdleTimeout: Duration
  get() = Registry.intValue(STREAMABLE_SESSION_IDLE_TIMEOUT_REGISTRY_KEY, 300_000).coerceAtLeast(1).milliseconds

/**
 * A session that never initialized holds an IDE-global override, the document conflict resolution. No client can reach
 * such a session, so the cap closes it much sooner than an ordinary idle session.
 */
private val UNINITIALIZED_SESSION_IDLE_CAP = 30.seconds

/**
 * A Streamable HTTP session, which lives independently of the requests that use it. A notification stream comes and
 * goes, and a client may never open one.
 *
 * A request and a stream each take a hold on the session while it runs, and a session with no hold is idle.
 * [closeWhenAbandoned] is the only thing that ends a session no client ended itself.
 */
internal class StreamableHttpSession(val transport: StreamableHttpServerTransport) {
  private sealed interface State {
    /** [revision] is what tells an idle moment apart from a later one that looks just like it. */
    data class Serving(val holds: Int, val revision: Long) : State

    /** Terminal: a closed session never serves again, so a hold released after the close needs no accounting. */
    data object Closed : State
  }

  private val state = MutableStateFlow<State>(State.Serving(holds = 0, revision = 0))

  private val isInitialized: Boolean
    get() = transport.sessionId != null

  private val idleTimeout: Duration
    get() =
      if (isInitialized) streamableSessionIdleTimeout
      else streamableSessionIdleTimeout.coerceAtMost(UNINITIALIZED_SESSION_IDLE_CAP)

  init {
    transport.onClose { state.value = State.Closed }
  }

  /**
   * Runs [request] on a session that is still open, or returns `false` because it is not. A notification stream is a
   * request as well, and it can hold the session for hours.
   */
  suspend fun runRequest(request: suspend () -> Unit): Boolean {
    if (!acquireHold()) return false
    try {
      request()
    }
    finally {
      releaseHold()
    }
    return true
  }

  /**
   * Closes the session when it stays idle for [idleTimeout], and closes it on cancellation as well. So the server
   * releases a session it still serves when it cancels the scope that runs this.
   */
  suspend fun closeWhenAbandoned(sessionId: String) {
    try {
      while (true) {
        when (val idle = awaitNoHolds()) {
          State.Closed -> return
          is State.Serving ->
            if (!changedWithin(idleTimeout, since = idle) && state.compareAndSet(idle, State.Closed)) {
              if (isInitialized) logger.info("$ABANDONED_SESSION_CLOSED. Id: $sessionId")
              else logger.warn("$UNINITIALIZED_SESSION_CLOSED. Id: $sessionId")
              return
            }
        }
      }
    }
    finally {
      transport.closeUninterruptibly()
    }
  }

  private fun acquireHold(): Boolean =
    state.getAndUpdate {
      if (it is State.Serving) it.copy(holds = it.holds + 1, revision = it.revision + 1) else it
    } is State.Serving

  private fun releaseHold() {
    state.update { if (it is State.Serving) it.copy(holds = it.holds - 1) else it }
  }

  private suspend fun awaitNoHolds(): State = state.first { it !is State.Serving || it.holds == 0 }

  private suspend fun changedWithin(timeout: Duration, since: State.Serving): Boolean =
    withTimeoutOrNull(timeout) { state.first { it != since } } != null
}
