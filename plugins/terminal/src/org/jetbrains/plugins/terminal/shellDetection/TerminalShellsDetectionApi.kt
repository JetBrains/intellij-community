// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.shellDetection

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface TerminalShellsDetectionApi : RemoteApi<Unit> {
  suspend fun detectShells(projectId: ProjectId): ShellsDetectionResult

  companion object {
    @JvmStatic
    suspend fun getInstance(): TerminalShellsDetectionApi {
      return RemoteApiProviderService.Companion.resolve(remoteApiDescriptor<TerminalShellsDetectionApi>())
    }
  }
}