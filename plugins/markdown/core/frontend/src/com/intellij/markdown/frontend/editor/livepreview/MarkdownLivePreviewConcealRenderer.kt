// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.intellij.plugins.markdown.editor.livepreview.toTextRange

internal class MarkdownLivePreviewConcealRenderer : MarkdownLivePreviewElementRenderer {
  override fun presentation(spec: MarkdownLivePreviewSpec): List<MarkdownLivePreviewFold> {
    val conceal = spec as MarkdownLivePreviewSpec.Conceal
    return conceal.conceals.map { MarkdownLivePreviewFold(it.toTextRange()) }
  }

  override fun documentChanged() = Unit
  override fun reconcile(presentation: MarkdownLivePreviewPresentation?) = Unit
  override fun dispose() = Unit
}
