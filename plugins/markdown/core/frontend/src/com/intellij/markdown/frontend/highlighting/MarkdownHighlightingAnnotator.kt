// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.markdown.frontend.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.util.LayeredTextAttributes
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.OuterLanguageElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parents
import com.intellij.util.text.CharArrayUtil
import org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors
import org.intellij.plugins.markdown.highlighting.MarkdownSyntaxHighlighter
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownAlertTitle
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.lang.psi.util.alertTitleColorKey
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.intellij.plugins.markdown.lang.psi.util.parentOfType
import org.intellij.plugins.markdown.util.isFootnoteContinuationBlock
import org.intellij.plugins.markdown.util.isFootnoteDefinitionLinkDef
import org.intellij.plugins.markdown.util.isFootnoteDefinitionParagraph
import org.intellij.plugins.markdown.util.isFootnoteLabelText

@Suppress("RegExpRedundantEscape")
private val FOOTNOTE_REF_IN_TEXT = Regex("""\[\^[^\]\n\t ]+]""")
private val CITATION_KEY_IN_LABEL = Regex("""(?:^|[\s;,])-?@[\p{L}\p{N}_][\p{L}\p{N}_:.#$%&+?<>~/\-]*""")

/**
 * Style keys to be applied to [PsiElement] with the following semantics:
 * `null` — rule does not apply, pick another one;
 * [emptySet] — handled, suppress generic highlighting.
 */
private typealias HighlightingKeys = Set<TextAttributesKey>

/** An alert is a blockquote with a title, so its lines get the blockquote style too. */
private val BlockQuoteTypes = TokenSet.create(MarkdownElementTypes.BLOCK_QUOTE, MarkdownElementTypes.ALERT)

internal class MarkdownHighlightingAnnotator : Annotator, DumbAware {
  private val syntaxHighlighter = MarkdownSyntaxHighlighter()

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (holder.isBatchMode()) return
    if (element.elementType is OuterLanguageElementType) return

    annotateBlockQuoteLines(element, holder)
    annotateInlineFootnoteMarker(element, holder)
    if (annotateShortReferenceSyntax(element, holder)) return
    if (annotateFootnoteContinuationCodeLine(element, holder)) return

    val keys = collectHighlightingKeys(element) ?: return
    applyAnnotations(holder, element, keys)
  }

  /**
   * Applies the style of [MarkdownHighlighterColors.BLOCK_QUOTE] without its background to each line of an outermost block quote.
   * A line starts at its first non-blank character. The background pass paints the background of the quote.
   * The bundled schemes give the style only a background, so they get no annotation.
   */
  private fun annotateBlockQuoteLines(element: PsiElement, holder: AnnotationHolder) {
    if (element.elementType !in BlockQuoteTypes || element.parents(withSelf = false).any { it.elementType in BlockQuoteTypes }) return
    val attributes = EditorColorsManager.getInstance().globalScheme.getAttributes(MarkdownHighlighterColors.BLOCK_QUOTE)?.clone() ?: return
    attributes.backgroundColor = null
    if (attributes.isEmpty) return
    val contents = element.containingFile.viewProvider.contents
    val quoteRange = element.textRange
    var lineStart = quoteRange.startOffset
    while (lineStart < quoteRange.endOffset) {
      val lineBreak = StringUtil.indexOf(contents, '\n', lineStart, quoteRange.endOffset)
      val lineEnd = if (lineBreak < 0) quoteRange.endOffset else lineBreak
      val start = CharArrayUtil.shiftForward(contents, lineStart, lineEnd, " \t")
      if (start < lineEnd) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
          .enforcedTextAttributes(attributes)
          .range(TextRange(start, lineEnd))
          .create()
      }
      lineStart = lineEnd + 1
    }
  }

  private fun annotateShortReferenceSyntax(element: PsiElement, holder: AnnotationHolder): Boolean {
    val linkLabel = element.parentOfType(MarkdownElementTypes.LINK_LABEL, withSelf = true) ?: return false
    val shortReferenceLink = linkLabel.parent?.takeIf { it.elementType == MarkdownElementTypes.SHORT_REFERENCE_LINK } ?: return false
    val isInlineFootnote = isInlineFootnote(shortReferenceLink)
    val highlightingKey = when {
      isInlineFootnote -> MarkdownHighlighterColors.INLINE_FOOTNOTE
      isCitationLabel(linkLabel.text) -> MarkdownHighlighterColors.CITATION
      else -> return false
    }

    if (element == linkLabel) return true

    val inheritedKeys = collectHighlightingKeys(element)
    if (inheritedKeys != null) {
      val keys = inheritedKeys.filterNot {
        it == MarkdownHighlighterColors.LINK_LABEL || it == MarkdownHighlighterColors.REFERENCE_LINK
      }.toSet() + highlightingKey
      applyAnnotations(holder, element, keys)
    }
    return true
  }

  private fun annotateInlineFootnoteMarker(element: PsiElement, holder: AnnotationHolder) {
    if (element.elementType != MarkdownTokenTypes.TEXT || !element.text.endsWith('^')) return
    val shortReferenceLink = element.nextSibling?.takeIf {
      it.elementType == MarkdownElementTypes.SHORT_REFERENCE_LINK
    } ?: return
    if (!isInlineFootnote(shortReferenceLink)) return

    val markerStartOffset = element.textRange.endOffset - 1
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
      .textAttributes(MarkdownHighlighterColors.INLINE_FOOTNOTE)
      .range(TextRange(markerStartOffset, markerStartOffset + 1))
      .create()
  }

  private fun isInlineFootnote(shortReferenceLink: PsiElement): Boolean {
    val startOffset = shortReferenceLink.textRange.startOffset
    if (startOffset == 0) return false

    val contents = shortReferenceLink.containingFile.viewProvider.contents
    if (contents[startOffset - 1] != '^') return false

    var backslashCount = 0
    var offset = startOffset - 2
    while (offset >= 0 && contents[offset] == '\\') {
      backslashCount++
      offset--
    }
    return backslashCount % 2 == 0
  }

  private fun isCitationLabel(text: String): Boolean {
    val labelText = text.removePrefix("[").removeSuffix("]")
    return CITATION_KEY_IN_LABEL.containsMatchIn(labelText)
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
    val ownStyleKey = element.primaryNonTextAttributesKey().takeUnless { element.isHtmlInCodeSpan() }
    val keys = if (ownStyleKey != null) {
      setOf(ownStyleKey)
    }
    else {
      val parents = element.parents(withSelf = false).toList()
      val isImageLinkContent = parents.any { it.elementType == MarkdownElementTypes.IMAGE } &&
                               parents.any { it.elementType == MarkdownElementTypes.INLINE_LINK }
      parents
        .asReversed()
        .filterNot { isImageLinkContent && it.elementType == MarkdownElementTypes.IMAGE }
        // annotateBlockQuoteLines and the background pass cover whole lines, so the leaves do not repeat the block quote style
        .filterNot { it.elementType == MarkdownElementTypes.BLOCK_QUOTE }
        .mapNotNullTo(linkedSetOf()) {
          it.primaryNonTextAttributesKey()
        }
    }

    return keys.takeIf {
      it.isNotEmpty()
    }
  }

  private fun applyAnnotations(holder: AnnotationHolder, element: PsiElement, keys: HighlightingKeys) {
    if (keys.isEmpty()) return

    val scheme = EditorColorsManager.getInstance().globalScheme
    val attributes = LayeredTextAttributes.create(scheme, keys.toTypedArray())
    // The code background pass paints a rounded background behind a code span and a code fence.
    if (!element.isHtmlInCodeSpan() && (MarkdownHighlighterColors.CODE_SPAN in keys || MarkdownHighlighterColors.CODE_FENCE in keys)) {
      attributes.backgroundColor = null
    }
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
      .enforcedTextAttributes(attributes)
      .range(element.textRange)
      .create()
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

  private fun isConsecutiveFootnoteReferenceLink(fullReferenceLink: PsiElement): Boolean {
    val linkText = fullReferenceLink.children.firstOrNull { it.elementType == MarkdownElementTypes.LINK_TEXT }
    val linkLabel = fullReferenceLink.children.firstOrNull { it.elementType == MarkdownElementTypes.LINK_LABEL }
    return isFootnoteLabelText(linkText?.text.orEmpty()) && isFootnoteLabelText(linkLabel?.text.orEmpty())
  }

  private fun getAlertAttributeKey(element: MarkdownAlertTitle): TextAttributesKey? {
    return element.getType()?.let(::alertTitleColorKey)
  }

  private fun PsiElement.isHtmlInCodeSpan(): Boolean =
    elementType == MarkdownTokenTypes.HTML_TAG && parentOfType(MarkdownElementTypes.CODE_SPAN) != null

  private fun PsiElement.primaryNonTextAttributesKey(): TextAttributesKey? {
    return syntaxHighlighter
      .getMarkdownTokenHighlights(elementType)
      .firstOrNull { it != MarkdownHighlighterColors.TEXT }
  }
}
