// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session.rpc

import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.terminal.session.TerminalInputEvent
import com.intellij.terminal.session.TerminalOutputEvent
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface TerminalSessionApi : RemoteApi<Unit> {
  /**
   * Note, that implementation should return the separate instance of the channel at least for each [com.intellij.codeWithMe.ClientId].
   * Because the RPC logic closes the returned channel once the client disconnects,
   * so it may interrupt the reading of the channel on the backend.
   */
  suspend fun getInputChannel(sessionId: TerminalSessionId): SendChannel<TerminalInputEvent>

  suspend fun getOutputFlow(sessionId: TerminalSessionId): Flow<List<TerminalOutputEvent>>

  companion object {
    @JvmStatic
    suspend fun getInstance(): TerminalSessionApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<TerminalSessionApi>())
    }
  }
}