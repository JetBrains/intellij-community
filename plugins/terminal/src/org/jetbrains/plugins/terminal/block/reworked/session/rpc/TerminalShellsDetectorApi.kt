// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session.rpc

import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.DetectedShellInfo

@ApiStatus.Internal
@Rpc
interface TerminalShellsDetectorApi : RemoteApi<Unit> {
  suspend fun detectShells(): List<DetectedShellInfo>

  companion object {
    @JvmStatic
    suspend fun getInstance(): TerminalShellsDetectorApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<TerminalShellsDetectorApi>())
    }
  }
}