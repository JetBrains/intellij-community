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
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownAlert
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownAlertTitle
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.intellij.plugins.markdown.util.isFootnoteLabelText

@Suppress("RegExpRedundantEscape")
private val FOOTNOTE_REF_IN_TEXT = Regex("""\[\^[^\]\n\t ]+]""")

internal class MarkdownHighlightingAnnotator : Annotator, DumbAware {
  private val syntaxHighlighter = MarkdownSyntaxHighlighter()

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (holder.isBatchMode()) return
    when (element.elementType) {
      MarkdownTokenTypes.ALERT_TITLE -> {
        val key = getAlertAttributeKey(element as MarkdownAlertTitle) ?: return
        element.traverseAndCreateAnnotationsForContent(holder, key)
      }
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
    val parentType = element.parent?.elementType ?: return
    val attributes = predicate.invoke(parentType)
    if (attributes != null) {
      element.traverseAndCreateAnnotationsForContent(holder, attributes)
    }
  }

  private fun annotateWithHighlighter(element: PsiElement, holder: AnnotationHolder) {
    val elementType = element.elementType
    if (elementType == MarkdownTokenTypes.HTML_BLOCK_CONTENT || elementType == MarkdownElementTypes.HTML_BLOCK) return
    if (elementType == MarkdownTokenTypes.CODE_LINE) {
      val codeBlock = element.parent
      if (codeBlock != null && isFootnoteContinuationBlock(codeBlock)) {
        element.traverseAndCreateAnnotationsForContent(holder, MarkdownHighlighterColors.FOOTNOTE_DEFINITION)
        highlightFootnoteRefsInCodeLine(element, holder)
        return
      }
    }
    if (elementType == MarkdownTokenTypes.TEXT) {
      val parent = element.parent
      if (parent != null && parent.hasType(MarkdownElementTypes.PARAGRAPH) && isFootnoteDefinitionParagraph(parent)) {
        element.traverseAndCreateAnnotationsForContent(holder, MarkdownHighlighterColors.FOOTNOTE_DEFINITION)
        return
      }
    }
    if (elementType == MarkdownElementTypes.LINK_DESTINATION) {
      val linkDef = element.parent
      if (linkDef != null && linkDef.hasType(MarkdownElementTypes.LINK_DEFINITION) && isFootnoteDefinitionLinkDef(linkDef)) {
        element.traverseAndCreateAnnotationsForContent(holder, MarkdownHighlighterColors.FOOTNOTE_DEFINITION)
        return
      }
    }
    if (elementType == MarkdownElementTypes.LINK_TEXT) {
      val parent = element.parent
      if (parent != null && parent.hasType(MarkdownElementTypes.FULL_REFERENCE_LINK) && isConsecutiveFootnoteReferenceLink(parent)) {
        // Apply LINK_LABEL color instead of LINK_TEXT (hyperlink) color, matching how SHORT_REFERENCE_LINK's LINK_LABEL is rendered
        element.traverseAndCreateAnnotationsForContent(holder, MarkdownHighlighterColors.LINK_LABEL)
        return
      }
    }
    if (element.hasType(MarkdownTokenTypes.CODE_FENCE_CONTENT) && (element.parent as? MarkdownCodeFence)?.fenceLanguage != null) {
      return
    }
    if (element.hasType(MarkdownTokenTypes.BLOCK_QUOTE) && PsiTreeUtil.getParentOfType(element, MarkdownAlert::class.java) != null) {
      element.traverseAndCreateAnnotationsForContent(holder, MarkdownHighlighterColors.TEXT)
      return
    }
    val highlights = syntaxHighlighter.getMarkdownTokenHighlights(elementType)
    val parentAttributesKey = highlights.firstOrNull() ?: return
    if (parentAttributesKey != MarkdownHighlighterColors.TEXT) {
      element.traverseAndCreateAnnotationsForContent(holder, parentAttributesKey)
    }
  }

  private fun highlightFootnoteRefsInCodeLine(codeLine: PsiElement, holder: AnnotationHolder) {
    val text = codeLine.text
    val base = codeLine.textRange.startOffset
    for (match in FOOTNOTE_REF_IN_TEXT.findAll(text)) {
      val range = TextRange(base + match.range.first, base + match.range.last + 1)
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
        .textAttributes(MarkdownHighlighterColors.LINK_LABEL)
        .range(range)
        .create()
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
        .textAttributes(MarkdownHighlighterColors.BOLD)
        .range(range)
        .create()
    }
  }

  private fun isFootnoteDefinitionParagraph(paragraph: PsiElement): Boolean {
    val firstChild = paragraph.firstChild ?: return false
    val linkLabel: PsiElement? = when (firstChild.elementType) {
      MarkdownElementTypes.SHORT_REFERENCE_LINK -> firstChild.firstChild
      MarkdownElementTypes.LINK_LABEL -> firstChild
      else -> null
    }
    if (linkLabel == null || !linkLabel.hasType(MarkdownElementTypes.LINK_LABEL)) return false
    return isFootnoteLabelText(linkLabel.text)
  }

  private fun isConsecutiveFootnoteReferenceLink(fullReferenceLink: PsiElement): Boolean {
    val linkText = fullReferenceLink.children.firstOrNull { it.elementType == MarkdownElementTypes.LINK_TEXT }
    val linkLabel = fullReferenceLink.children.firstOrNull { it.elementType == MarkdownElementTypes.LINK_LABEL }
    return isFootnoteLabelText(linkText?.text.orEmpty()) && isFootnoteLabelText(linkLabel?.text.orEmpty())
  }

  private fun isFootnoteContinuationBlock(codeBlock: PsiElement): Boolean {
    for (sibling in codeBlock.siblings(forward = false, withSelf = false)) {
      when {
        sibling.hasType(MarkdownElementTypes.PARAGRAPH) -> return isFootnoteDefinitionParagraph(sibling)
        sibling.hasType(MarkdownElementTypes.LINK_DEFINITION) -> return isFootnoteDefinitionLinkDef(sibling)
        sibling.hasType(MarkdownElementTypes.CODE_BLOCK) -> continue
        sibling.firstChild == null -> continue  // skip leaf tokens (EOL, whitespace)
        else -> return false
      }
    }
    return false
  }

  private fun getAlertAttributeKey(element: MarkdownAlertTitle): TextAttributesKey? {
    return when (element.getType()) {
      MarkdownAlertTitle.AlertType.NOTE -> MarkdownHighlighterColors.ALERT_TITLE_NOTE
      MarkdownAlertTitle.AlertType.TIP -> MarkdownHighlighterColors.ALERT_TITLE_TIP
      MarkdownAlertTitle.AlertType.IMPORTANT -> MarkdownHighlighterColors.ALERT_TITLE_IMPORTANT
      MarkdownAlertTitle.AlertType.WARNING -> MarkdownHighlighterColors.ALERT_TITLE_WARNING
      MarkdownAlertTitle.AlertType.CAUTION -> MarkdownHighlighterColors.ALERT_TITLE_CAUTION
      null -> null
    }
  }

  private fun isFootnoteDefinitionLinkDef(linkDef: PsiElement): Boolean {
    val linkLabel = linkDef.firstChild ?: return false
    if (!linkLabel.hasType(MarkdownElementTypes.LINK_LABEL)) return false
    return isFootnoteLabelText(linkLabel.text)
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
