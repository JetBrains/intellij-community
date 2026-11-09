// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.folding.MarkdownFoldingBuilder
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

internal class MarkdownHeaderSectionExtendWordSelectionHandler : ExtendWordSelectionHandlerBase() {
  override fun canSelect(e: PsiElement): Boolean = e.parent is MarkdownFile

  override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange> {
    val ranges = ArrayList<TextRange>()
    val visitor = MarkdownFoldingBuilder.HeaderRegionsBuildingVisitor { header, range ->
      if (header.parent === e.parent && range.contains(e.textRange)) ranges += expandToWholeLine(editorText, range)
    }
    e.containingFile.accept(visitor)
    visitor.processLastHeaderIfNeeded()
    return ranges
  }
}
