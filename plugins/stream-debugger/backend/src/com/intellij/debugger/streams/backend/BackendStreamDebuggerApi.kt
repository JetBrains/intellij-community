package com.intellij.debugger.streams.backend

import com.intellij.debugger.streams.core.ChainStatus
import com.intellij.debugger.streams.core.ChainDetectionStateManager
import com.intellij.debugger.streams.core.action.TraceStreamRunner
import com.intellij.debugger.streams.core.statistics.TraceEntryPoint
import com.intellij.debugger.streams.shared.ChainStatusDto
import com.intellij.debugger.streams.shared.StreamDebuggerApi
import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.models.findValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

internal class BackendStreamDebuggerApi : StreamDebuggerApi {
  override suspend fun getChainStatus(sessionId: XDebugSessionId): Flow<ChainStatusDto> {
    val session = sessionId.findValue() ?: return emptyFlow()
    return ChainDetectionStateManager
      .getInstance(session.project)
      .chainStateFlow(session)
      .map { it.status.toDto() }
      .distinctUntilChanged()
  }

  override suspend fun showTraceDebuggerDialog(sessionId: XDebugSessionId) {
    val session = sessionId.findValue() ?: return
    TraceStreamRunner.getInstance(session.project).actionPerformed(session, TraceEntryPoint.TOOLBAR_ACTION)
  }
}

private fun ChainStatus.toDto(): ChainStatusDto = when (this) {
  ChainStatus.Computing -> ChainStatusDto.COMPUTING
  ChainStatus.LanguageNotSupported -> ChainStatusDto.LANGUAGE_NOT_SUPPORTED
  ChainStatus.NotFound -> ChainStatusDto.NOT_FOUND
  is ChainStatus.Found -> ChainStatusDto.FOUND
}
