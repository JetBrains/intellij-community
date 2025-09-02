// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.service

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.intellij.plugins.markdown.dto.MarkdownHeaderInfo
import org.intellij.plugins.markdown.dto.MarkdownLinkNavigationData
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface MarkdownLinkOpenerRemoteApi: RemoteApi<Unit> {
  suspend fun fetchLinkNavigationData(link: String, virtualFileId: VirtualFileId?): MarkdownLinkNavigationData

  companion object {
    @JvmStatic
    suspend fun getInstance(): MarkdownLinkOpenerRemoteApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<MarkdownLinkOpenerRemoteApi>())
    }
  }
}