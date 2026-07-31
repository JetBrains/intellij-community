// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.markdown.backend.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.OuterLanguageElementType
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parents
import com.intellij.psi.util.siblings
import org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors
import org.intellij.plugins.markdown.highlighting.MarkdownSyntaxHighlighter
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownAlertTitle
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.lang.psi.util.alertTitleColorKey
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.intellij.plugins.markdown.lang.psi.util.parentOfType
import org.intellij.plugins.markdown.util.isFootnoteLabelText

@Suppress("RegExpRedundantEscape")
private val FOOTNOTE_REF_IN_TEXT = Regex("""\[\^[^\]\n\t ]+]""")

/**
 * Style keys to be applied to [PsiElement] with the following semantics:
 * `null` — rule does not apply, pick another one;
 * [emptySet] — handled, suppress generic highlighting.
 */
private typealias HighlightingKeys = Set<TextAttributesKey>

internal class MarkdownHighlightingAnnotator : Annotator, DumbAware {
  private val syntaxHighlighter = MarkdownSyntaxHighlighter()

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (holder.isBatchMode()) return
    if (element.elementType is OuterLanguageElementType) return

    if (annotateFootnoteContinuationCodeLine(element, holder)) return

    val keys = collectHighlightingKeys(element) ?: return
    applyAnnotations(holder, element, keys)
  }

  private fun annotateFootnoteContinuationCodeLine(element: PsiElement, holder: AnnotationHolder): Boolean {
    if (element.elementType != MarkdownTokenTypes.CODE_LINE) return false
    val codeBlock = element.parent ?: return false
    if (!isFootnoteContinuationBlock(codeBlock)) return false

    highlightFootnoteRefsInCodeLine(element, holder)
    applyAnnotations(holder, element, setOf(MarkdownHighlighterColors.FOOTNOTE_DEFINITION))
    return true
  }

  private fun collectHighlightingKeys(element: PsiElement): HighlightingKeys? =
    tryAnnotateContextualDelimiter(element)
    ?: tryAnnotateSpecialCase(element)
    ?: collectLeafKeys(element)

  private fun collectLeafKeys(element: PsiElement): HighlightingKeys? {
    // Generic inherited styles are pulled only by leaf elements
    if (element.firstChild != null) return null

    // Infer ancestor style only when has no own highlight
    val ownStyleKey = element.primaryNonTextAttributesKey()
    val keys = if (ownStyleKey != null) {
      setOf(ownStyleKey)
    }
    else {
      element.parents(withSelf = false)
        .toList()
        .asReversed()
        .mapNotNullTo(linkedSetOf()) {
          it.primaryNonTextAttributesKey()
        }
    }

    return keys.takeIf {
      it.isNotEmpty()
    }
  }

  private fun applyAnnotations(holder: AnnotationHolder, element: PsiElement, keys: HighlightingKeys) {
    for (key in keys) {
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
        .textAttributes(key)
        .range(element.textRange)
        .create()
    }
  }

  private fun annotateBasedOnParent(
    element: PsiElement,
    predicate: (IElementType) -> TextAttributesKey?,
  ): TextAttributesKey? {
    val parentType = element.parent?.elementType ?: return null
    return predicate.invoke(parentType)
  }

  private fun tryAnnotateContextualDelimiter(element: PsiElement): HighlightingKeys? {
    val collectedKey = when (element.elementType) {
      MarkdownTokenTypes.ALERT_TITLE -> getAlertAttributeKey(element as MarkdownAlertTitle)
      MarkdownTokenTypes.EMPH -> annotateBasedOnParent(element) {
        when (it) {
          MarkdownElementTypes.EMPH -> MarkdownHighlighterColors.ITALIC_MARKER
          MarkdownElementTypes.STRONG -> MarkdownHighlighterColors.BOLD_MARKER
          else -> null
        }
      }
      MarkdownTokenTypes.BACKTICK -> annotateBasedOnParent(element) {
        when (it) {
          MarkdownElementTypes.CODE_FENCE -> MarkdownHighlighterColors.CODE_FENCE_MARKER
          MarkdownElementTypes.CODE_SPAN -> MarkdownHighlighterColors.CODE_SPAN_MARKER
          else -> null
        }
      }
      MarkdownTokenTypes.DOLLAR -> annotateBasedOnParent(element) {
        when (it) {
          MarkdownElementTypes.BLOCK_MATH -> MarkdownHighlighterColors.CODE_FENCE_MARKER
          MarkdownElementTypes.INLINE_MATH -> MarkdownHighlighterColors.CODE_SPAN_MARKER
          else -> null
        }
      }
      else -> return null
    }

    return if (collectedKey != null) {
      setOf(collectedKey)
    }
    else {
      emptySet()
    }
  }

  /**
   * Handles highlighting cases that cannot be expressed via [MarkdownSyntaxHighlighter.ATTRIBUTES]
   * or ancestor inheritance alone, providing special style overrides for contextual tokens.
   */
  private fun tryAnnotateSpecialCase(element: PsiElement): HighlightingKeys? {
    val elementType = element.elementType
    val hasHtml = elementType == MarkdownTokenTypes.HTML_BLOCK_CONTENT ||
                  element.parentOfType(MarkdownElementTypes.HTML_BLOCK) != null
    // scope of HTML highlighter
    if (hasHtml) {
      return emptySet()
    }
    tryAnnotateFootnoteDefinition(element)?.let {
      return it
    }
    tryAnnotateConsecutiveFootnoteReference(element)?.let {
      return it
    }

    return when {
      element.hasType(MarkdownTokenTypes.CODE_FENCE_CONTENT) && (element.parent as? MarkdownCodeFence)?.fenceLanguage != null -> {
        emptySet()
      }
      element.hasType(MarkdownTokenTypes.BLOCK_QUOTE) && element.parentOfType(MarkdownElementTypes.ALERT) != null -> {
        setOf(MarkdownHighlighterColors.TEXT)
      }
      else -> null
    }
  }

  private fun tryAnnotateFootnoteDefinition(element: PsiElement): HighlightingKeys? {
    val elementType = element.elementType
    if (elementType == MarkdownTokenTypes.TEXT) {
      val parent = element.parent
      if (parent != null && parent.hasType(MarkdownElementTypes.PARAGRAPH) && isFootnoteDefinitionParagraph(parent)) {
        return setOf(MarkdownHighlighterColors.FOOTNOTE_DEFINITION)
      }
    }
    val linkDestination = element.parentOfType(MarkdownElementTypes.LINK_DESTINATION, withSelf = true)
    if (linkDestination != null) {
      val linkDef = linkDestination.parent
      if (linkDef != null && linkDef.hasType(MarkdownElementTypes.LINK_DEFINITION) && isFootnoteDefinitionLinkDef(linkDef)) {
        return if (linkDestination == element) {
          setOf(MarkdownHighlighterColors.FOOTNOTE_DEFINITION)
        }
        else {
          emptySet()
        }
      }
    }

    return null
  }

  // Apply LINK_LABEL color instead of LINK_TEXT (hyperlink) color, matching how SHORT_REFERENCE_LINK's LINK_LABEL is rendered
  private fun tryAnnotateConsecutiveFootnoteReference(element: PsiElement): HighlightingKeys? =
    sequenceOf(MarkdownElementTypes.LINK_TEXT, MarkdownElementTypes.LINK_LABEL)
      .firstNotNullOfOrNull {
        tryAnnotateConsecutiveFootnoteReferencePart(element, it)
      }

  private fun tryAnnotateConsecutiveFootnoteReferencePart(
    element: PsiElement,
    partType: IElementType,
  ): HighlightingKeys? {
    val part = element.parentOfType(partType, withSelf = true) ?: return null
    val parent = part.parent
    if (parent == null || !parent.hasType(MarkdownElementTypes.FULL_REFERENCE_LINK)) return null
    if (!isConsecutiveFootnoteReferenceLink(parent)) return null

    return if (part == element) {
      setOf(MarkdownHighlighterColors.LINK_LABEL)
    }
    else {
      emptySet()
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
    return element.getType()?.let(::alertTitleColorKey)
  }

  private fun isFootnoteDefinitionLinkDef(linkDef: PsiElement): Boolean {
    val linkLabel = linkDef.firstChild ?: return false
    if (!linkLabel.hasType(MarkdownElementTypes.LINK_LABEL)) return false
    return isFootnoteLabelText(linkLabel.text)
  }

  private fun PsiElement.primaryNonTextAttributesKey(): TextAttributesKey? {
    return syntaxHighlighter
      .getMarkdownTokenHighlights(elementType)
      .firstOrNull { it != MarkdownHighlighterColors.TEXT }
  }
}
