// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes

class MarkdownCodeSpanExtendWordSelectionHandler: ExtendWordSelectionHandlerBase() {
  override fun canSelect(element: PsiElement): Boolean {
    return element.parent?.takeIf { PsiUtilCore.getElementType(it) == MarkdownElementTypes.CODE_SPAN } != null
  }

  override fun select(element: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): MutableList<TextRange>? {
    val originalSelections = super.select(element, editorText, cursorOffset, editor)
    val left = ensureCorrectElement(element.parent?.firstChild) ?: return originalSelections
    val right = ensureCorrectElement(element.parent?.lastChild) ?: return originalSelections
    return mutableListOf(TextRange(left.endOffset, right.startOffset))
  }

  private fun ensureCorrectElement(element: PsiElement?): PsiElement? {
    return element?.takeIf { PsiUtilCore.getElementType(element) == MarkdownTokenTypes.BACKTICK }
  }
}

