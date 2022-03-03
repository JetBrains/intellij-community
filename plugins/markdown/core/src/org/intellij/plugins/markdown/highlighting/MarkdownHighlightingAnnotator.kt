// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiUtilCore
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.util.hasType

class MarkdownHighlightingAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    when (PsiUtilCore.getElementType(element)) {
      MarkdownTokenTypes.EMPH -> annotateBasedOnParent(element, holder) {
        when (it) {
          MarkdownElementTypes.EMPH -> MarkdownHighlighterColors.ITALIC_MARKER_ATTR_KEY
          MarkdownElementTypes.STRONG -> MarkdownHighlighterColors.BOLD_MARKER_ATTR_KEY
          else -> null
        }
      }
      MarkdownTokenTypes.BACKTICK -> annotateBasedOnParent(element, holder) {
        when (it) {
          MarkdownElementTypes.CODE_FENCE -> MarkdownHighlighterColors.CODE_FENCE_MARKER_ATTR_KEY
          MarkdownElementTypes.CODE_SPAN -> MarkdownHighlighterColors.CODE_SPAN_MARKER_ATTR_KEY
          else -> null
        }
      }
      else -> annotateWithHighlighter(element, holder)
    }
  }

  private fun annotateBasedOnParent(element: PsiElement, holder: AnnotationHolder, predicate: (IElementType) -> TextAttributesKey?) {
    val parentType = element.parent?.let(PsiUtilCore::getElementType) ?: return
    val attributes = predicate.invoke(parentType)
    if (attributes != null) {
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION).textAttributes(attributes).create()
    }
  }

  private fun annotateWithHighlighter(element: PsiElement, holder: AnnotationHolder) {
    if (element.hasType(MarkdownTokenTypes.CODE_FENCE_CONTENT) && (element.parent as? MarkdownCodeFence)?.fenceLanguage != null) {
      return
    }
    val highlights = syntaxHighlighter.getTokenHighlights(PsiUtilCore.getElementType(element))
    if (highlights.isNotEmpty() && highlights.first() != MarkdownHighlighterColors.TEXT_ATTR_KEY) {
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION).textAttributes(highlights.first()).create()
    }
  }

  companion object {
    private val syntaxHighlighter = MarkdownSyntaxHighlighter()
  }
}
