// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.openapi.editor.Editor
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpecSet
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewReconcilerBridge

internal class MarkdownLivePreviewReconcilerBridgeImpl : MarkdownLivePreviewReconcilerBridge {
  override fun hasExistingReconciler(editor: Editor): Boolean = MarkdownLivePreviewReconciler.getExisting(editor) != null

  override fun publish(editor: Editor, specSet: MarkdownLivePreviewSpecSet): Boolean {
    val reconciler = MarkdownLivePreviewReconciler.getOrCreate(editor) ?: return false
    reconciler.publishSpecs(specSet)
    return true
  }
}
