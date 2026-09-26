// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.FoldingKeys
import com.intellij.openapi.util.TextRange

/** Replaces source with a text placeholder and an optional decoration. */
internal data class MarkdownLivePreviewTextFold(
  override val range: TextRange,
  val placeholderText: String = "",
  override val decoration: MarkdownLivePreviewFoldDecoration? = null,
) : MarkdownLivePreviewFold {
  override fun isSame(region: FoldRegion): Boolean =
    region !is CustomFoldRegion && region.placeholderText == placeholderText

  override fun create(editor: EditorEx): FoldRegion? {
    val foldingModel = editor.foldingModel
    val existing = foldingModel.getFoldRegion(range.startOffset, range.endOffset)
    val region =
      when {
        existing == null -> foldingModel.createFoldRegion(range.startOffset, range.endOffset, placeholderText, null, true)
        existing.isValid && existing.shouldNeverExpand() && isSame(existing) -> existing
        else -> null
      } ?: return null
    if (placeholderText.isNotEmpty()) region.putUserData(FoldingKeys.HIDE_PLACEHOLDER_BACKGROUND, true)
    return region
  }
}
