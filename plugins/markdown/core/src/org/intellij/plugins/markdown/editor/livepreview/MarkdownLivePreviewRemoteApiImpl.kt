// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.editor.impl.findEditorOrNull
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.getOrCreateUserData
import fleet.rpc.core.RpcFlow
import fleet.rpc.core.toRpc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.dropWhile
import org.jetbrains.annotations.ApiStatus

private val LIVE_PREVIEW_SPEC_SET_FLOW = Key.create<MutableStateFlow<MarkdownLivePreviewSpecSet?>>("markdown.live.preview.spec.set.flow")

/**
 * Holds the configuration of the live-preview specs that the frontend of an editor asks for.
 * The key is separate from the shared live-preview state, so a late request never overwrites a newer frontend state.
 */
private val LIVE_PREVIEW_REQUESTED = Key.create<MarkdownLivePreviewConfiguration>("markdown.live.preview.requested")

internal fun Editor.livePreviewSpecSetFlow(): MutableStateFlow<MarkdownLivePreviewSpecSet?> {
  return (this as UserDataHolderEx).getOrCreateUserData(LIVE_PREVIEW_SPEC_SET_FLOW) { MutableStateFlow(null) }
}

internal fun Editor.requestedLivePreviewConfiguration(): MarkdownLivePreviewConfiguration? {
  return getUserData(LIVE_PREVIEW_REQUESTED)
}

@ApiStatus.Internal
class MarkdownLivePreviewRemoteApiImpl : MarkdownLivePreviewRemoteApi {
  override suspend fun getLivePreviewSpecs(editorId: EditorId): RpcFlow<MarkdownLivePreviewSpecSet?> {
    val specSets = editorId.findEditorOrNull()?.livePreviewSpecSetFlow() ?: return RpcFlow.empty()
    return specSets.dropWhile { it == null }.toRpc()
  }

  override suspend fun setLivePreviewSupport(editorId: EditorId, configuration: MarkdownLivePreviewConfiguration?) {
    val editor = editorId.findEditorOrNull() ?: return
    val previous = editor.requestedLivePreviewConfiguration()
    editor.putUserData(LIVE_PREVIEW_REQUESTED, configuration)
    if (configuration != null) {
      editor.publishCurrentLivePreviewSpecs(force = previous != configuration)
    }
  }

  override suspend fun requestLivePreviewImage(editorId: EditorId, destination: String) {
    editorId.findEditorOrNull()?.getOrCreateMarkdownLivePreviewImageManager()?.load(destination)
  }
}
