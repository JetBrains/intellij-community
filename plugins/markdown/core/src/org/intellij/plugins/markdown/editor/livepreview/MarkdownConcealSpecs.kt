// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiUtilCore
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.jetbrains.annotations.ApiStatus

/**
 * Elements we report specs for but never look inside.
 *
 * Code keeps its markup literal, so `` `**not bold**` `` must show its asterisks. Images and tables are
 * not part of the live preview yet: descending into an image would reach the [MarkdownElementTypes.INLINE_LINK]
 * it wraps and hide the link syntax while leaving a stray `!` behind, so `![alt](url)` stays fully visible
 * instead. A link destination is already covered whole by the `](url)` conceal, so there is nothing to
 * find inside it, and a wrapped autolink holds the very brackets it already reports - descending would
 * report them a second time through its inner leaf.
 */
private val NoDescendTypes: Set<IElementType> = setOf(
  MarkdownElementTypes.CODE_FENCE,
  MarkdownElementTypes.CODE_BLOCK,
  MarkdownElementTypes.CODE_SPAN,
  MarkdownElementTypes.HTML_BLOCK,
  MarkdownElementTypes.IMAGE,
  MarkdownElementTypes.TABLE,
  MarkdownElementTypes.LINK_DESTINATION,
  MarkdownElementTypes.AUTOLINK,
)

/**
 * The leaf autolink tokens, which the parser does not wrap in an element of their own.
 *
 * `<name@example.org>` is parsed flat - the brackets are siblings of the address rather than a wrapper - and
 * a bare GFM autolink has no brackets at all, so [bracketedSiblingAutolinkConceals] tells them apart by
 * looking for the sibling brackets.
 */
private val LeafAutolinkTypes: Set<IElementType> = setOf(
  MarkdownTokenTypes.AUTOLINK,
  MarkdownTokenTypes.EMAIL_AUTOLINK,
  MarkdownTokenTypes.GFM_AUTOLINK,
)

/**
 * The inline markup live preview can hide in [file], sorted by element start offset.
 *
 * Pure function of the PSI: which of these are actually hidden depends on where the carets are, and that
 * is decided when the specs are applied.
 *
 * Collects the markup to hide, skipping the subtrees named in [NoDescendTypes].
 */
@ApiStatus.Internal
fun computeConcealElements(file: PsiFile): List<MarkdownConcealElement> {
  return SyntaxTraverser.psiTraverser(file)
    .expand { PsiUtilCore.getElementType(it) !in NoDescendTypes }
    .asSequence()
    .mapNotNull { it.toConcealElement() }
    .sortedWith(compareBy({ it.range.startOffset }, { it.range.endOffset }))
    .toList()
}

private fun PsiElement.toConcealElement(): MarkdownConcealElement? {
  return when (PsiUtilCore.getElementType(this)) {
    // One `*` or `_` is one EMPH token, so `**bold**` has two of them on each side, and the token type is
    // shared by emphasis and strong. A nested emphasis element is a composite of a different type, so it
    // never gets mistaken for a delimiter.
    MarkdownElementTypes.STRONG, MarkdownElementTypes.EMPH -> delimiterConceals(MarkdownTokenTypes.EMPH)
    MarkdownElementTypes.STRIKETHROUGH -> delimiterConceals(MarkdownTokenTypes.TILDE)
    MarkdownElementTypes.CODE_SPAN -> delimiterConceals(MarkdownTokenTypes.BACKTICK)
    MarkdownElementTypes.INLINE_LINK -> inlineLinkConceals()
    // `<https://example.org>` becomes a composite holding the brackets, while `<name@example.org>` stays
    // flat and keeps them as siblings, so the two forms need different lookups.
    MarkdownElementTypes.AUTOLINK -> wrappedAutolinkConceals()
    in LeafAutolinkTypes -> bracketedSiblingAutolinkConceals()
    else -> null
  }
}

/** Hides the runs of [delimiter] that open and close this element, as in `**bold**` or `` `code` ``. */
private fun PsiElement.delimiterConceals(delimiter: IElementType): MarkdownConcealElement? {
  val children = childList()
  val leading = children.takeWhile { PsiUtilCore.getElementType(it) == delimiter }
  val trailing = children.takeLastWhile { PsiUtilCore.getElementType(it) == delimiter }
  if (leading.isEmpty() || trailing.isEmpty()) return null
  // The element is malformed or empty, and the two runs are the same tokens.
  if (leading.last().textRange.endOffset > trailing.first().textRange.startOffset) return null
  return concealElement(
    TextRange(leading.first().textRange.startOffset, leading.last().textRange.endOffset),
    TextRange(trailing.first().textRange.startOffset, trailing.last().textRange.endOffset),
  )
}

/** Hides `[` and `](destination)` of `[title](destination)`, leaving the title as plain text. */
private fun PsiElement.inlineLinkConceals(): MarkdownConcealElement? {
  val linkText = childList().firstOrNull { PsiUtilCore.getElementType(it) == MarkdownElementTypes.LINK_TEXT } ?: return null
  val textChildren = linkText.childList()
  val openBracket = textChildren.firstOrNull()?.takeIf { PsiUtilCore.getElementType(it) == MarkdownTokenTypes.LBRACKET } ?: return null
  val closeBracket = textChildren.lastOrNull()?.takeIf { PsiUtilCore.getElementType(it) == MarkdownTokenTypes.RBRACKET } ?: return null
  val end = textRange.endOffset
  // An empty title, or a link whose destination part is missing, has nothing worth hiding.
  if (closeBracket.textRange.startOffset <= openBracket.textRange.endOffset || end <= closeBracket.textRange.startOffset) {
    return null
  }
  return concealElement(openBracket.textRange, TextRange(closeBracket.textRange.startOffset, end))
}

/** Hides the angle brackets of a wrapped autolink such as `<https://example.org>`. */
private fun PsiElement.wrappedAutolinkConceals(): MarkdownConcealElement? {
  val range = textRange
  if (range.length < 2) return null
  val text = text
  if (!text.startsWith('<') || !text.endsWith('>')) return null
  return concealElement(
    TextRange(range.startOffset, range.startOffset + 1),
    TextRange(range.endOffset - 1, range.endOffset),
  )
}

/**
 * Hides the angle brackets around an autolink that the parser left flat, as in `<name@example.org>`.
 *
 * The element covering the whole autolink is the bracket pair itself, so revealing works the same way as
 * for the wrapped form. A bare autolink has no brackets and is left alone.
 */
private fun PsiElement.bracketedSiblingAutolinkConceals(): MarkdownConcealElement? {
  val openBracket = prevSibling?.takeIf { PsiUtilCore.getElementType(it) == MarkdownTokenTypes.LT } ?: return null
  val closeBracket = nextSibling?.takeIf { PsiUtilCore.getElementType(it) == MarkdownTokenTypes.GT } ?: return null
  return MarkdownConcealElement(
    range = TextRange(openBracket.textRange.startOffset, closeBracket.textRange.endOffset),
    conceals = listOf(openBracket.textRange, closeBracket.textRange),
  )
}

private fun PsiElement.concealElement(vararg conceals: TextRange): MarkdownConcealElement? {
  val ranges = conceals.filterNot { it.isEmpty }
  return if (ranges.isEmpty()) null else MarkdownConcealElement(textRange, ranges)
}

private fun PsiElement.childList(): List<PsiElement> = node.getChildren(null).map { it.psi }
