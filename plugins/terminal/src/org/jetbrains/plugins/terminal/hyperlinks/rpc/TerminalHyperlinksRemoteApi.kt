// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface TerminalHyperlinksRemoteApi : RemoteApi<Unit> {
  suspend fun createNewSession(projectId: ProjectId): TerminalHyperlinksSessionId

  suspend fun closeSession(sessionId: TerminalHyperlinksSessionId)

  companion object {
    suspend fun getInstance(): TerminalHyperlinksRemoteApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<TerminalHyperlinksRemoteApi>())
    }
  }
}