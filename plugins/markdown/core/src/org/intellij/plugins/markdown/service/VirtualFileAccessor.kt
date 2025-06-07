// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.service

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface VirtualFileAccessor : RemoteApi<Unit> {
  suspend fun getFileByResourceName(resourceName: String, virtualFileId: VirtualFileId, projectId: ProjectId): VirtualFileId?

  companion object {
    @JvmStatic
    suspend fun getInstance(): VirtualFileAccessor {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<VirtualFileAccessor>())
    }
  }
}