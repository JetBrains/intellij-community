// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.markdown.frontend.editor.tables.ui.alignment.MarkdownTableAlignmentController
import com.intellij.openapi.editor.Editor
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.intellij.plugins.markdown.editor.livepreview.toTextRange
import org.intellij.plugins.markdown.editor.tables.ui.presentation.HorizontalBarPresentation

internal class MarkdownLivePreviewConcealRenderer(private val editor: Editor) : MarkdownLivePreviewElementRenderer {
  override fun presentation(spec: MarkdownLivePreviewSpec): List<MarkdownLivePreviewFold> {
    val conceal = spec as MarkdownLivePreviewSpec.Conceal
    return conceal.conceals.map { MarkdownLivePreviewTextFold(it.toTextRange()) }
  }

  override fun documentChanged() = Unit
  override fun reconcile(presentation: MarkdownLivePreviewPresentation?) {
    MarkdownTableAlignmentController.getExisting(editor)?.performRefresh()
    HorizontalBarPresentation.refresh(editor)
  }
  override fun dispose() = Unit
}
