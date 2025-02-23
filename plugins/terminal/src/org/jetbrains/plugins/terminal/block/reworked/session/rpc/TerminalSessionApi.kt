// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session.rpc

import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.terminal.session.TerminalInputEvent
import com.intellij.terminal.session.TerminalOutputEvent
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface TerminalSessionApi : RemoteApi<Unit> {
  suspend fun sendInputEvent(sessionId: TerminalSessionId, event: TerminalInputEvent)

  suspend fun getOutputFlow(sessionId: TerminalSessionId): Flow<List<TerminalOutputEvent>>

  companion object {
    @JvmStatic
    suspend fun getInstance(): TerminalSessionApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<TerminalSessionApi>())
    }
  }
}