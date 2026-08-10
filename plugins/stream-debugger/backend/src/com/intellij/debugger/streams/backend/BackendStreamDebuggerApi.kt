package com.intellij.debugger.streams.backend

import com.intellij.debugger.streams.core.ChainStatus
import com.intellij.debugger.streams.core.ChainDetectionStateManager
import com.intellij.debugger.streams.core.action.TraceStreamRunner
import com.intellij.debugger.streams.shared.ChainStateDto
import com.intellij.debugger.streams.shared.StreamDebuggerApi
import com.intellij.debugger.streams.shared.TraceEntryPoint
import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.models.findValue
import com.intellij.xdebugger.impl.rpc.toRpc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

internal class BackendStreamDebuggerApi : StreamDebuggerApi {
  override suspend fun getChainState(sessionId: XDebugSessionId): Flow<ChainStateDto> {
    val session = sessionId.findValue() ?: return emptyFlow()
    return ChainDetectionStateManager
      .getInstance(session.project)
      .chainStateFlow(session)
      .map { it.status.toDto() }
      .distinctUntilChanged()
  }

  override suspend fun showTraceDebuggerDialog(sessionId: XDebugSessionId, entryPoint: TraceEntryPoint) {
    val session = sessionId.findValue() ?: return
    TraceStreamRunner.getInstance(session.project).actionPerformed(session, entryPoint)
  }
}

private suspend fun ChainStatus.toDto(): ChainStateDto = when (this) {
  ChainStatus.Computing -> ChainStateDto.Computing
  ChainStatus.LanguageNotSupported -> ChainStateDto.LanguageNotSupported
  ChainStatus.NotFound -> ChainStateDto.NotFound
  is ChainStatus.Found -> ChainStateDto.Found(position.toRpc())
}
