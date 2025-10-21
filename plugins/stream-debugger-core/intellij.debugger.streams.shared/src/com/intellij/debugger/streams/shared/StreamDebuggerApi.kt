package com.intellij.debugger.streams.shared

import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface StreamDebuggerApi : RemoteApi<Unit> {
  suspend fun getChainStatus(sessionId: XDebugSessionId): Flow<ChainStatus>
  suspend fun showTraceDebuggerDialog(sessionId: XDebugSessionId)

  companion object {
    @JvmStatic
    suspend fun getInstance(): StreamDebuggerApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<StreamDebuggerApi>())
    }
  }
}
