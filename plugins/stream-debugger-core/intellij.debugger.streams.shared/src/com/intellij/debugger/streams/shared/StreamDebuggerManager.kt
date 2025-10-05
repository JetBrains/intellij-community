package com.intellij.debugger.streams.shared

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.xdebugger.impl.FrontendXDebuggerManagerListener
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
internal class StreamDebuggerManager(project: Project) : FrontendXDebuggerManagerListener {
  private val sessionStates = ConcurrentHashMap<XDebugSessionId, TraceDebuggerStateListener>()

  init {
    project.messageBus.connect().subscribe(FrontendXDebuggerManagerListener.TOPIC, this)
  }


  override fun sessionStarted(session: XDebugSessionProxy) {
    sessionStates[session.id] = TraceDebuggerStateListener(session.coroutineScope, session.id)
  }

  override fun sessionStopped(session: XDebugSessionProxy) {
    sessionStates.remove(session.id)
  }

  fun getChainStatus(id: XDebugSessionId): ChainStatus? {
    return sessionStates[id]?.chainStatus
  }

  companion object {
    fun getInstance(project: Project): StreamDebuggerManager = project.service()
  }
}

private class TraceDebuggerStateListener(cs: CoroutineScope, sessionId: XDebugSessionId) {
  private val _state = MutableStateFlow(null as ChainStatus?)


  init {
    cs.launch {
      StreamDebuggerApi.getInstance().getChainStatus(sessionId).collectLatest {
        _state.value = it
      }
    }
  }

  val chainStatus: ChainStatus?
    get() = _state.value
}


private class TraceDebuggerInitializationProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    StreamDebuggerManager.getInstance(project)
  }
}