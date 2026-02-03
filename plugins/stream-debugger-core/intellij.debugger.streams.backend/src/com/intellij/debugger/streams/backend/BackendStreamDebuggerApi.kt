package com.intellij.debugger.streams.backend

import com.intellij.debugger.streams.core.action.TraceStreamRunner
import com.intellij.debugger.streams.shared.ChainStatus
import com.intellij.debugger.streams.shared.StreamDebuggerApi
import com.intellij.openapi.application.smartReadAction
import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.util.asDisposable
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.impl.rpc.models.findValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapLatest

@OptIn(ExperimentalCoroutinesApi::class)
internal class BackendStreamDebuggerApi : StreamDebuggerApi {
  override suspend fun getChainStatus(sessionId: XDebugSessionId): Flow<ChainStatus> {
    val session = sessionId.findValue() ?: return emptyFlow()
    return channelFlow {
      session.addSessionListener(object : XDebugSessionListener {
        override fun sessionPaused() {
          trySend(Unit)
        }

        override fun beforeSessionResume() {
          trySend(Unit)
        }

        override fun sessionResumed() {
          trySend(Unit)
        }

        override fun sessionStopped() {
          trySend(Unit)
        }

        override fun stackFrameChanged() {
          trySend(Unit)
        }
      }, this.asDisposable())
      send(Unit)
      awaitClose()
    }.buffer(Channel.UNLIMITED)
      .mapLatest { fetchStatus(session) }
  }

  override suspend fun showTraceDebuggerDialog(sessionId: XDebugSessionId) {
    val session = sessionId.findValue() ?: return
    // ChainResolver.getChains is expected to be called right after TraceStreamRunner#getChainStatus call
    // due to caching in #mySearchResult
    fetchStatus(session)
    TraceStreamRunner.getInstance(session.project).actionPerformed(session)
  }

  private suspend fun fetchStatus(session: XDebugSession): ChainStatus {
    val project = session.project
    return smartReadAction(project) { TraceStreamRunner.getInstance(project).getChainStatus(session) }
  }
}
