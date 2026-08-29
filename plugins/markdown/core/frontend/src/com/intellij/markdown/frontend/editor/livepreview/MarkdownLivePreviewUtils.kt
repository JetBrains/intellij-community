// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.codeInsight.documentation.render.DocRenderer
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import java.util.LinkedHashMap
import java.util.SequencedMap

internal fun Editor.getAllCurrentImageLayout(): SequencedMap<TextRange, Int> {
  return foldingModel.allFoldRegions
    .asSequence()
    .filterIsInstance<CustomFoldRegion>()
    .filter { it.markdownImageRenderItem() != null }
    .associateTo(LinkedHashMap()) { it.textRange to it.heightInPixels }
}

internal fun CustomFoldRegion.markdownImageRenderItem(): MarkdownImageRenderItem? {
  if (!isValid) return null
  return (renderer as? DocRenderer)?.item as? MarkdownImageRenderItem
}
