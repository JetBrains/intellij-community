// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.diff

import com.intellij.diff.frontend.FrontendDiffContext
import com.intellij.diff.frontend.FrontendDiffExtension
import com.intellij.diff.frontend.FrontendDiffRequest
import com.intellij.diff.frontend.FrontendDiffViewer
import com.intellij.markdown.frontend.actions.isDiffLivePreviewEnabled
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import org.intellij.plugins.markdown.editor.livepreview.setLivePreviewEnabledness
import org.intellij.plugins.markdown.lang.supportsMarkdown

/**
 * Enables live preview for Markdown editors in a new diff viewer.
 * A new viewer starts in the state that the user chose last.
 *
 * The platform calls this extension in the monolith and on a Remote Development client alike, so one code path serves both.
 */
internal class MarkdownDiffLivePreviewExtension : FrontendDiffExtension {
  override fun install(context: FrontendDiffContext, request: FrontendDiffRequest, viewer: FrontendDiffViewer, disposable: Disposable) {
    val editors = viewer.editors().filter { it.supportsMarkdown() }
    if (isDiffLivePreviewEnabled()) {
      editors.forEach { it.setLivePreviewEnabledness(true) }
    }
  }
}

private fun FrontendDiffViewer.editors(): List<Editor> = when (this) {
  is FrontendDiffViewer.TwoSide -> listOf(leftEditor, rightEditor)
  is FrontendDiffViewer.OneSide -> listOf(editor)
  is FrontendDiffViewer.Unified -> emptyList()
}
