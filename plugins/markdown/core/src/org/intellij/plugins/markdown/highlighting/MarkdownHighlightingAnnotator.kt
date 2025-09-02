// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.OuterLanguageElementType
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.elementType
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.lang.psi.util.hasType

internal class MarkdownHighlightingAnnotator : Annotator, DumbAware {
  private val syntaxHighlighter = MarkdownSyntaxHighlighter()

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    when (PsiUtilCore.getElementType(element)) {
      MarkdownTokenTypes.EMPH -> annotateBasedOnParent(element, holder) {
        when (it) {
          MarkdownElementTypes.EMPH -> MarkdownHighlighterColors.ITALIC_MARKER
          MarkdownElementTypes.STRONG -> MarkdownHighlighterColors.BOLD_MARKER
          else -> null
        }
      }
      MarkdownTokenTypes.BACKTICK -> annotateBasedOnParent(element, holder) {
        when (it) {
          MarkdownElementTypes.CODE_FENCE -> MarkdownHighlighterColors.CODE_FENCE_MARKER
          MarkdownElementTypes.CODE_SPAN -> MarkdownHighlighterColors.CODE_SPAN_MARKER
          else -> null
        }
      }
      MarkdownTokenTypes.DOLLAR -> annotateBasedOnParent(element, holder) {
        when (it) {
          MarkdownElementTypes.BLOCK_MATH -> MarkdownHighlighterColors.CODE_FENCE_MARKER
          MarkdownElementTypes.INLINE_MATH -> MarkdownHighlighterColors.CODE_SPAN_MARKER
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
      element.traverseAndCreateAnnotationsForContent(holder, attributes)
    }
  }

  private fun annotateWithHighlighter(element: PsiElement, holder: AnnotationHolder) {
    if (element.hasType(MarkdownTokenTypes.CODE_FENCE_CONTENT) && (element.parent as? MarkdownCodeFence)?.fenceLanguage != null) {
      return
    }
    val highlights = syntaxHighlighter.getTokenHighlights(PsiUtilCore.getElementType(element))
    val parentAttributesKey = highlights.firstOrNull() ?: return
    if (parentAttributesKey != MarkdownHighlighterColors.TEXT) {
      element.traverseAndCreateAnnotationsForContent(holder, parentAttributesKey)
    }
  }

  /**
   * Traverse element's subtree and create annotations corresponding only to Markdown content,
   * ignoring nodes which might be of [OuterLanguageElementType].
   * Applying for leaf elements would result in a guarantee of non-overlapping ranges.
   */
  private fun PsiElement.traverseAndCreateAnnotationsForContent(holder: AnnotationHolder, textAttributesKey: TextAttributesKey) {
    val contentRanges = mutableListOf<TextRange>()

    accept(object : PsiRecursiveElementVisitor() {
      override fun visitElement(element: PsiElement) {
        val type = element.elementType
        if (type !is OuterLanguageElementType && element.firstChild == null) {
          contentRanges.add(element.textRange)
        }

        super.visitElement(element)
      }
    })
    /**
     * If an original sequence was separated by [OuterLanguageElementType]s, then there are several ranges.
     * In other cases, the result contains one element.
     */
    val mergedRanges = mergeRanges(contentRanges)

    mergedRanges.forEach { contentRange ->
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
        .textAttributes(textAttributesKey)
        .range(contentRange)
        .create()
    }
  }

  /**
   * Given the list of content [TextRange]s, reduces the list by merging adjustment [TextRange]s, e.g.,
   * for any i, j from the given list: start_i == end_j.
   */
  private fun mergeRanges(contentRanges: Collection<TextRange>): Collection<TextRange> {
    if (contentRanges.isEmpty()) {
      return emptyList()
    }

    val mergedRanges = mutableSetOf<TextRange>()
    val sortedRanges = contentRanges
      .sortedBy { it.startOffset }
    var currentRange = sortedRanges[0]

    for (i in 1 until sortedRanges.size) {
      val nextRange = sortedRanges[i]

      if (currentRange.endOffset == nextRange.startOffset) {
        currentRange = TextRange(
          currentRange.startOffset,
          maxOf(currentRange.endOffset, nextRange.endOffset)
        )
      } else {
        mergedRanges.add(currentRange)
        currentRange = nextRange
      }
    }

    mergedRanges.add(currentRange)

    return mergedRanges
  }
}
