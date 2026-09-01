// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.openapi.editor.impl.EditorId
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.core.RpcFlow
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

/** Provides Markdown live-preview specs from the editor backend. */
@ApiStatus.Internal
@Rpc
interface MarkdownLivePreviewRemoteApi : RemoteApi<Unit> {
  suspend fun getLivePreviewSpecs(editorId: EditorId): RpcFlow<MarkdownLivePreviewSpecSet>

  suspend fun requestLivePreviewImage(editorId: EditorId, destination: String)

  companion object {
    @JvmStatic
    suspend fun getInstance(): MarkdownLivePreviewRemoteApi {
      return LiteRemoteApiProviderService.awaitConnectionAndResolve(remoteApiDescriptor<MarkdownLivePreviewRemoteApi>())
    }
  }
}
