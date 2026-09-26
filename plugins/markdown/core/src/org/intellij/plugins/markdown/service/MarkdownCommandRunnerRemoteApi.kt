// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.service

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface MarkdownCommandRunnerRemoteApi : RemoteApi<Unit> {
  suspend fun filterRunnable(
    projectId: ProjectId,
    virtualFileId: VirtualFileId?,
    commands: List<String>,
    allowRunConfigurations: Boolean,
  ): Set<String>

  suspend fun execute(
    projectId: ProjectId,
    virtualFileId: VirtualFileId?,
    command: String,
    executorId: String,
    workingDirectory: String,
  ): Boolean

  suspend fun runBlock(
    projectId: ProjectId,
    command: String,
    executorId: String,
    workingDirectory: String,
  ): Boolean

  suspend fun frontendRunnerRequests(): Flow<MarkdownFrontendRunnerRequest>

  suspend fun isProjectTrusted(projectId: ProjectId): Boolean

  suspend fun setProjectTrusted(projectId: ProjectId)

  companion object {
    suspend fun getInstance(): MarkdownCommandRunnerRemoteApi {
      return LiteRemoteApiProviderService.awaitConnectionAndResolve(remoteApiDescriptor<MarkdownCommandRunnerRemoteApi>())
    }

    @JvmStatic
    fun tryGetInstance(): MarkdownCommandRunnerRemoteApi? {
      return LiteRemoteApiProviderService.tryResolve(remoteApiDescriptor<MarkdownCommandRunnerRemoteApi>())
    }
  }
}
