// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.editor.livepreview

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.editor.impl.findEditorOrNull
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.core.RpcFlow
import fleet.rpc.core.toRpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.dropWhile
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewRemoteApi
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpecSet

private val LIVE_PREVIEW_SPEC_SET_FLOW = Key.create<MutableStateFlow<MarkdownLivePreviewSpecSet?>>("markdown.live.preview.spec.set.flow")

/**
 * Set when the frontend of an editor asks for live-preview specs.
 * The key is separate from the shared live-preview flag, so a late request never overwrites a newer frontend state.
 */
private val LIVE_PREVIEW_REQUESTED = Key.create<Boolean>("markdown.live.preview.requested")

internal fun Editor.livePreviewSpecSetFlow(): MutableStateFlow<MarkdownLivePreviewSpecSet?> {
  return (this as UserDataHolderEx).getOrCreateUserData(LIVE_PREVIEW_SPEC_SET_FLOW) { MutableStateFlow(null) }
}

internal fun Editor.isLivePreviewRequested(): Boolean {
  return getUserData(LIVE_PREVIEW_REQUESTED) == true
}

private class MarkdownLivePreviewRemoteApiImpl : MarkdownLivePreviewRemoteApi {
  override suspend fun getLivePreviewSpecs(editorId: EditorId): RpcFlow<MarkdownLivePreviewSpecSet?> {
    val specSets = editorId.findEditorOrNull()?.livePreviewSpecSetFlow() ?: return RpcFlow.empty()
    return specSets.dropWhile { it == null }.toRpc()
  }

  override suspend fun setLivePreviewSupport(editorId: EditorId, enabled: Boolean) {
    val editor = editorId.findEditorOrNull() ?: return
    editor.putUserData(LIVE_PREVIEW_REQUESTED, enabled)
    if (enabled) {
      editor.publishCurrentLivePreviewSpecs()
    }
  }

  override suspend fun requestLivePreviewImage(editorId: EditorId, destination: String) {
    editorId.findEditorOrNull()?.getOrCreateMarkdownLivePreviewImageManager()?.load(destination)
  }
}

internal class MarkdownLivePreviewRemoteApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<MarkdownLivePreviewRemoteApi>()) {
      MarkdownLivePreviewRemoteApiImpl()
    }
  }
}
