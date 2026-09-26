package com.intellij.debugger.streams.shared

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * Sentinel for an already finished session.
 */
private val SESSION_FINISHED: StateFlow<ChainStateDto?> = MutableStateFlow(null)

/**
 * Caches the chain state from the backend ([StreamDebuggerApi.getChainState])
 * It is reused between the toolbar action and the inlay.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class StreamDebuggerManager {
  private val sessionStates = ConcurrentHashMap<XDebugSessionId, TraceDebuggerStateListener>()

  /**
   * The state is created lazily, on the first request, and lives in the session scope.
   *
   * A listener does not work here. `FrontendXDebuggerManager` publishes `sessionStarted`. It replays the event for
   * sessions that are already running, but only once, at its own start.
   *
   * The message bus does not replay events for a subscriber that connects later.
   * Startup activities create both `StreamDebuggerManager` and `FrontendXDebuggerManager`, and they run in any order.
   * So `StreamDebuggerManager` can subscribe after the replay and never see some running sessions.
   *
   * Subscriber order is not defined either, so even an early subscriber can lose the race: the inlay may ask for
   * the state before the listener creates it.
   */
  fun chainStateFlow(session: XDebugSessionProxy): StateFlow<ChainStateDto?> {
    sessionStates[session.id]?.let { return it.chainStateFlow }
    val sessionJob = session.coroutineScope.coroutineContext.job
    if (!sessionJob.isActive) return SESSION_FINISHED
    val state = sessionStates.computeIfAbsent(session.id) { TraceDebuggerStateListener(session.coroutineScope, it) }
    sessionJob.invokeOnCompletion { sessionStates.remove(session.id, state) }
    return state.chainStateFlow
  }

  companion object {
    fun getInstance(project: Project): StreamDebuggerManager = project.service()
  }
}

private class TraceDebuggerStateListener(cs: CoroutineScope, sessionId: XDebugSessionId) {
  private val _state = MutableStateFlow<ChainStateDto?>(null)

  val chainStateFlow: StateFlow<ChainStateDto?> = _state.asStateFlow()

  init {
    cs.launch(CoroutineName("StreamDebuggerStateListener[sessionId=$sessionId]")) {
      StreamDebuggerApi.getInstance().getChainState(sessionId).collect { _state.value = it }
    }
  }
}