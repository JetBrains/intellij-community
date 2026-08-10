package com.intellij.debugger.streams.shared

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.xdebugger.impl.XDebuggerManagerProxyListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches the chain state from the backend ([StreamDebuggerApi.getChainState])
 * It is reused between the toolbar action and the inlay.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class StreamDebuggerManager(project: Project) : XDebuggerManagerProxyListener {
  private val sessionStates = ConcurrentHashMap<XDebugSessionId, TraceDebuggerStateListener>()

  init {
    project.messageBus.connect().subscribe(XDebuggerManagerProxyListener.TOPIC, this)
  }


  override fun sessionStarted(session: XDebugSessionProxy) {
    sessionState(session)
  }

  override fun sessionStopped(session: XDebugSessionProxy) {
    sessionStates.remove(session.id)?.stop()
  }

  fun chainStateFlow(session: XDebugSessionProxy): StateFlow<ChainStateDto?> = sessionState(session).chainStateFlow

  private fun sessionState(session: XDebugSessionProxy): TraceDebuggerStateListener =
    sessionStates.computeIfAbsent(session.id) { TraceDebuggerStateListener(session.coroutineScope, it) }

  companion object {
    fun getInstance(project: Project): StreamDebuggerManager = project.service()
  }
}

private class TraceDebuggerStateListener(cs: CoroutineScope, sessionId: XDebugSessionId) {
  private val _state = MutableStateFlow<ChainStateDto?>(null)

  private val subscription = cs.launch {
    StreamDebuggerApi.getInstance().getChainState(sessionId).collect { _state.value = it }
  }

  val chainStateFlow: StateFlow<ChainStateDto?> = _state.asStateFlow()

  fun stop() {
    subscription.cancel()
  }
}


internal class TraceDebuggerInitializationProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    StreamDebuggerManager.getInstance(project)
  }
}