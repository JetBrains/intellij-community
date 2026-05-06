// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session.rpc

import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

/**
 * Split Mode only RPC for notifying backend about user activity in a terminal.
 *
 * In split mode all terminal logic lives on the frontend,
 * so the backend host needs to be explicitly notified about user activity in the terminal.
 */
@ApiStatus.Internal
@Rpc
interface TerminalActivityTrackerRemoteApi : RemoteApi<Unit> {
  /**
   * Notifies the backend that there has been recent user activity in a terminal.
   */
  suspend fun registerActivity()

  companion object {
    suspend fun getInstance(): TerminalActivityTrackerRemoteApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<TerminalActivityTrackerRemoteApi>())
    }
  }
}