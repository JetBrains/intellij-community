// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.editor.livepreview

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewDocumentVersion
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewRange
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpecSet
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewUtils
import org.intellij.plugins.markdown.editor.livepreview.toMarkdownLivePreviewRange
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownAlertTitle
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownImage
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListItem
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.jetbrains.annotations.ApiStatus

/**
 * Elements we report specs for but never look inside.
 */
private val NoDescendTypes: Set<IElementType> = setOf(
  MarkdownElementTypes.CODE_FENCE,
  MarkdownElementTypes.CODE_BLOCK,
  MarkdownElementTypes.CODE_SPAN,
  MarkdownElementTypes.HTML_BLOCK,
  MarkdownElementTypes.IMAGE,
  MarkdownElementTypes.LINK_DESTINATION,
  MarkdownElementTypes.AUTOLINK,
)

/**
 * The leaf autolink tokens, which the parser does not wrap in an element of their own.
 *
 * `<name@example.org>` is parsed flat - the brackets are siblings of the address rather than a wrapper - and
 * a bare GFM autolink has no brackets at all, so [toAutolinkSpecs] tells them apart by
 * looking for the sibling brackets.
 */
private val LeafAutolinkTypes: Set<IElementType> = setOf(
  MarkdownTokenTypes.AUTOLINK,
  MarkdownTokenTypes.EMAIL_AUTOLINK,
  MarkdownTokenTypes.GFM_AUTOLINK,
)

private const val BULLET_PLACEHOLDERS = "•◦▪"

/**
 * The live-preview spec set for [file] in [editor], sorted by element start offset.
 *
 * Collects the markup to hide, skipping the subtrees named in [NoDescendTypes].
 */
@ApiStatus.Internal
fun computeLivePreviewSpecs(file: PsiFile, editor: Editor): MarkdownLivePreviewSpecSet {
  val blockQuotes by lazy { BlockQuoteSpecBuilder(file.viewProvider.contents, editor.document) }
  val elements = SyntaxTraverser.psiTraverser(file)
    .expand { PsiUtilCore.getElementType(it) !in NoDescendTypes }
    .asSequence()
    .flatMap {
      when (PsiUtilCore.getElementType(it)) {
        MarkdownElementTypes.BLOCK_QUOTE -> blockQuotes.create(it.textRange)
        MarkdownElementTypes.ALERT -> blockQuotes.create(it.textRange, it.alertType())
        else -> listOfNotNull(it.toDecorationSpecs(editor))
      }
    }
    .sortedWith(compareBy({ it.range.startOffset }, { it.range.endOffset }))
    .toList()
  val version = MarkdownLivePreviewDocumentVersion.capture(editor.document, file.project)
  return MarkdownLivePreviewSpecSet(version, elements)
}

private fun PsiElement.toDecorationSpecs(editor: Editor): MarkdownLivePreviewSpec? {
  return when (PsiUtilCore.getElementType(this)) {
    // One `*` or `_` is one EMPH token, so `**bold**` has two of them on each side, and the token type is
    // shared by emphasis and strong. A nested emphasis element is a composite of a different type, so it
    // never gets mistaken for a delimiter.
    MarkdownElementTypes.STRONG, MarkdownElementTypes.EMPH -> delimiterConceals(MarkdownTokenTypes.EMPH)
    MarkdownElementTypes.STRIKETHROUGH -> delimiterConceals(MarkdownTokenTypes.TILDE)
    MarkdownElementTypes.HIGHLIGHT -> delimiterConceals(MarkdownTokenTypes.EQUALS)
    MarkdownElementTypes.CODE_SPAN -> delimiterConceals(MarkdownTokenTypes.BACKTICK)
    MarkdownElementTypes.INLINE_LINK -> toInlineLinkSpecs()
    MarkdownElementTypes.FULL_REFERENCE_LINK, MarkdownElementTypes.SHORT_REFERENCE_LINK -> toReferenceLinkSpecs()
    MarkdownElementTypes.IMAGE -> if (isInsideTable()) null else toImageSpec(editor)
    MarkdownElementTypes.CODE_FENCE -> (this as? MarkdownCodeFence)?.toCodeFenceSpec()
    // `<https://example.org>` becomes a composite holding the brackets, while `<name@example.org>` stays
    // flat and keeps them as siblings, so the two forms need different lookups.
    MarkdownElementTypes.AUTOLINK -> toAutolinkSpecs()
    in LeafAutolinkTypes -> toAutolinkSpecs()
    in MarkdownTokenTypeSets.HEADERS -> (this as? MarkdownHeader)?.toHeadingSpec(editor)
    MarkdownTokenTypes.LIST_BULLET -> toBulletSpec()
    MarkdownTokenTypes.CHECK_BOX -> toTaskCheckboxSpec()
    MarkdownTokenTypes.HORIZONTAL_RULE -> toHorizontalRuleSpec()
    MarkdownElementTypes.FRONT_MATTER_HEADER_DELIMITER -> toFrontMatterDelimiterSpec()
    MarkdownTokenTypes.SETEXT_1, MarkdownTokenTypes.SETEXT_2 -> toSetextUnderlineSpec()
    MarkdownTokenTypes.ALERT_TITLE -> toAlertTitleSpec()
    else -> null
  }
}

/** Indexes blockquote markers once and creates one spec per marker in PSI traversal order. */
private class BlockQuoteSpecBuilder(private val source: CharSequence, private val document: Document) {
  private val markersByLine = HashMap<Int, List<Int>>()
  private val markersByDepth = mutableListOf<MutableList<MarkdownLivePreviewRange>>()
  /** The run of markers on the line of each marker, keyed by the marker offset. */
  private val markerRuns = HashMap<Int, MarkdownLivePreviewRange>()
  private val quoteEnds = ArrayDeque<Int>()

  init {
    for (line in 0 until document.lineCount) {
      val markers = source.blockQuoteMarkerOffsets(document.getLineStartOffset(line), document.getLineEndOffset(line))
      if (markers.isEmpty()) continue
      markersByLine[line] = markers
      for ((depth, offset) in markers.withIndex()) {
        if (depth == markersByDepth.size) markersByDepth.add(mutableListOf())
        markersByDepth[depth].add(MarkdownLivePreviewRange(offset, offset + 1))
      }
      indexRuns(markers)
    }
  }

  /** Splits the markers of one line into runs. Only spaces and tabs separate the markers of a run. */
  private fun indexRuns(markers: List<Int>) {
    var runStart = 0
    for ((index, offset) in markers.withIndex()) {
      val next = markers.getOrNull(index + 1)
      if (next != null && (offset + 1 until next).all { source[it] in " \t" }) continue
      val run = MarkdownLivePreviewRange(markers[runStart], offset + 1)
      for (marker in markers.subList(runStart, index + 1)) markerRuns[marker] = run
      runStart = index + 1
    }
  }

  fun create(blockQuoteRange: TextRange, alertType: MarkdownLivePreviewSpec.AlertType? = null): List<MarkdownLivePreviewSpec.BlockQuote> {
    while (quoteEnds.isNotEmpty() && quoteEnds.last() <= blockQuoteRange.startOffset) quoteEnds.removeLast()
    quoteEnds.addLast(blockQuoteRange.endOffset)

    val firstLine = document.getLineNumber(blockQuoteRange.startOffset)
    val firstLineMarkers = markersByLine[firstLine] ?: return emptyList()
    val firstMarkerIndex = firstLineMarkers.binarySearch(blockQuoteRange.startOffset).let { if (it < 0) -it - 1 else it }
    if (firstMarkerIndex == firstLineMarkers.size) return emptyList()

    val markers = markersByDepth.getOrNull(maxOf(firstMarkerIndex, quoteEnds.size - 1)) ?: return emptyList()
    val start = markers.firstAtOrAfter(blockQuoteRange.startOffset)
    val end = markers.firstAtOrAfter(blockQuoteRange.endOffset)
    val quoteMarkers = markers.subList(start, end)
    return quoteMarkers.mapIndexed { index, marker ->
      val next = quoteMarkers.getOrNull(index + 1)
      val ruleEnd =
        if (next != null) document.getLineStartOffset(document.getLineNumber(next.startOffset))
        else document.getLineEndOffset(document.getLineNumber(marker.startOffset))
      val placeholder = if (source.getOrNull(marker.endOffset)?.let { it in " \t" } == true) " " else "  "
      MarkdownLivePreviewSpec.BlockQuote(
        range = markerRuns.getValue(marker.startOffset),
        markerRange = marker,
        ruleRange = MarkdownLivePreviewRange(marker.startOffset, ruleEnd),
        placeholderText = placeholder,
        alertType = alertType,
      )
    }
  }

  private fun List<MarkdownLivePreviewRange>.firstAtOrAfter(offset: Int): Int {
    val index = binarySearchBy(offset) { it.startOffset }
    return if (index < 0) -index - 1 else index
  }
}

private fun CharSequence.blockQuoteMarkerOffsets(lineStart: Int, lineEnd: Int): List<Int> =
  buildList {
    var offset = lineStart
    while (offset < lineEnd) {
      while (offset < lineEnd && this@blockQuoteMarkerOffsets[offset] in " \t") offset++
      when {
        offset >= lineEnd -> break
        this@blockQuoteMarkerOffsets[offset] == '>' -> {
          add(offset)
          offset++
          if (offset < lineEnd && this@blockQuoteMarkerOffsets[offset] in " \t") offset++
        }
        else -> {
          val markerEnd = MarkdownLivePreviewUtils.getListMarkerEnd(this@blockQuoteMarkerOffsets, offset, lineEnd)
          if (markerEnd == offset || markerEnd == lineEnd) break
          offset = this@blockQuoteMarkerOffsets.taskCheckboxEnd(markerEnd, lineEnd)
        }
      }
    }
  }

/** Skips the task checkbox after a list marker, so that a blockquote after `- [ ]` keeps its marker. */
private fun CharSequence.taskCheckboxEnd(offset: Int, lineEnd: Int): Int {
  var cursor = offset
  while (cursor < lineEnd && this[cursor] in " \t") cursor++
  return if (cursor + 3 < lineEnd && MarkdownLivePreviewUtils.isCheckbox(this, cursor) && this[cursor + 3] in " \t") cursor + 3 else offset
}

private fun PsiElement.alertType(): MarkdownLivePreviewSpec.AlertType? {
  return when ((node.findChildByType(MarkdownTokenTypes.ALERT_TITLE)?.psi as? MarkdownAlertTitle)?.getType()) {
    MarkdownAlertTitle.AlertType.NOTE -> MarkdownLivePreviewSpec.AlertType.NOTE
    MarkdownAlertTitle.AlertType.TIP -> MarkdownLivePreviewSpec.AlertType.TIP
    MarkdownAlertTitle.AlertType.IMPORTANT -> MarkdownLivePreviewSpec.AlertType.IMPORTANT
    MarkdownAlertTitle.AlertType.WARNING -> MarkdownLivePreviewSpec.AlertType.WARNING
    MarkdownAlertTitle.AlertType.CAUTION -> MarkdownLivePreviewSpec.AlertType.CAUTION
    null -> null
  }
}

/** Hides `[!` and `]` of `[!NOTE]`, leaving the alert type as text. */
private fun PsiElement.toAlertTitleSpec(): MarkdownLivePreviewSpec.Conceal? {
  val range = textRange
  if (range.length < 4 || !text.startsWith("[!") || !text.endsWith("]")) return null
  return inlineConceal(TextRange.from(range.startOffset, 2), TextRange.from(range.endOffset - 1, 1))
}

private fun PsiElement.isInsideTable(): Boolean = PsiTreeUtil.getParentOfType(this, MarkdownTable::class.java) != null

private fun MarkdownHeader.toHeadingSpec(editor: Editor): MarkdownLivePreviewSpec.Heading? {
  val content = contentElement
  if (parent !is MarkdownFile || content == null) return null
  val range = logicalLineRange(editor)
  val html = HeadingHtmlGenerator.generate(content, range.startOffset)
  return MarkdownLivePreviewSpec.Heading(range.toMarkdownLivePreviewRange(), level, html)
}

private fun PsiElement.toImageSpec(editor: Editor): MarkdownLivePreviewSpec.Image? {
  val image = this as? MarkdownImage ?: return null
  val linkDestination = image.linkDestination ?: return null
  val destination = linkDestination.text.markdownDestination()
  if (!destination.isLocalDestination()) return null
  val range = image.wholeLineRange() ?: image.textRange
  val altText = image.collectLinkDescriptionText()?.let(StringUtil::collapseWhiteSpace).orEmpty()
  val placeholderText = altText.ifEmpty { MarkdownBundle.message("markdown.live.preview.image.placeholder") }
  val source = editor.getOrCreateMarkdownLivePreviewImageManager().findImageData(destination)
  return MarkdownLivePreviewSpec.Image(range.toMarkdownLivePreviewRange(), destination, placeholderText, source)
}

private fun MarkdownCodeFence.toCodeFenceSpec(): MarkdownLivePreviewSpec.CodeFence? {
  val language = fenceLanguage ?: return null
  if (node.findChildByType(MarkdownTokenTypes.CODE_FENCE_END) == null) return null
  val source = MarkdownCodeFence.obtainFenceContent(this, withWhitespaces = false)?.joinToString("") { it.text } ?: return null
  return MarkdownLivePreviewSpec.CodeFence(textRange.toMarkdownLivePreviewRange(), language, source)
}

/** Paints the `===` or `---` line under a Setext heading as a horizontal rule. */
private fun PsiElement.toSetextUnderlineSpec(): MarkdownLivePreviewSpec.HorizontalRule? {
  return if (parent is MarkdownHeader) toHorizontalRuleSpec() else null
}

private fun PsiElement.toHorizontalRuleSpec(): MarkdownLivePreviewSpec.HorizontalRule {
  return MarkdownLivePreviewSpec.HorizontalRule((wholeLineRange() ?: textRange).toMarkdownLivePreviewRange())
}

private fun PsiElement.toFrontMatterDelimiterSpec(): MarkdownLivePreviewSpec? {
  return if (text.all { it == '-' }) toHorizontalRuleSpec() else null
}

private fun PsiElement.wholeLineRange(): TextRange? {
  var start = textRange.startOffset
  var previous = PsiTreeUtil.prevLeaf(this, true)
  while (previous != null) {
    val text = previous.text
    val lineBreak = maxOf(text.lastIndexOf('\n'), text.lastIndexOf('\r'))
    if (lineBreak >= 0) {
      if (!text.isBlank(lineBreak + 1, text.length)) return null
      start = previous.textRange.startOffset + lineBreak + 1
      break
    }
    if (!text.isBlank(0, text.length)) return null
    start = previous.textRange.startOffset
    previous = PsiTreeUtil.prevLeaf(previous, true)
  }

  var end = textRange.endOffset
  var next = PsiTreeUtil.nextLeaf(this, true)
  while (next != null) {
    val text = next.text
    val lineBreak = text.firstLineBreak()
    if (lineBreak >= 0) {
      if (!text.isBlank(0, lineBreak)) return null
      end = next.textRange.startOffset + lineBreak
      break
    }
    if (!text.isBlank(0, text.length)) return null
    end = next.textRange.endOffset
    next = PsiTreeUtil.nextLeaf(next, true)
  }
  return TextRange(start, end)
}

private fun PsiElement.toBulletSpec(): MarkdownLivePreviewSpec.Bullet? {
  val listItem = parent ?: return null
  if (PsiUtilCore.getElementType(listItem) != MarkdownElementTypes.LIST_ITEM) return null
  if (PsiUtilCore.getElementType(listItem.parent) != MarkdownElementTypes.UNORDERED_LIST ||
      PsiUtilCore.getElementType(nextSibling) == MarkdownTokenTypes.CHECK_BOX) {
    return null
  }
  val markerOffset = text.indexOfFirst { it in "-*+" }
  if (markerOffset < 0) return null
  val depth = generateSequence(listItem) { it.parent }
    .count { PsiUtilCore.getElementType(it) == MarkdownElementTypes.LIST_ITEM }
  val markerStart = textRange.startOffset + markerOffset
  return MarkdownLivePreviewSpec.Bullet(
    range = MarkdownLivePreviewRange(markerStart, markerStart + 1),
    placeholderText = BULLET_PLACEHOLDERS[(depth - 1) % BULLET_PLACEHOLDERS.length].toString(),
  )
}

private fun PsiElement.logicalLineRange(editor: Editor): TextRange {
  val document = editor.document
  val line = document.getLineNumber(textRange.startOffset)
  return TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line))
}

private fun PsiElement.toTaskCheckboxSpec(): MarkdownLivePreviewSpec.TaskCheckbox? {
  val item = parent as? MarkdownListItem ?: return null
  if (item.checkBox != this) return null
  val text = text
  if (text.length < 3 || !MarkdownLivePreviewUtils.isCheckbox(text, 0)) return null
  val checkboxRange = TextRange(textRange.startOffset, textRange.startOffset + 3)
  val marker = item.markerElement ?: return null
  val concealStart = if (PsiUtilCore.getElementType(item.parent) == MarkdownElementTypes.UNORDERED_LIST) {
    val markerOffset = marker.text.indexOfFirst { it in "-*+" }
    if (markerOffset < 0) return null
    marker.textRange.startOffset + markerOffset
  }
  else checkboxRange.startOffset
  return MarkdownLivePreviewSpec.TaskCheckbox(
    range = MarkdownLivePreviewRange(concealStart, checkboxRange.endOffset),
    checked = text[1] != ' ',
  )
}

/** Hides the runs of [delimiter] that open and close this element, as in `**bold**` or `` `code` ``. */
private fun PsiElement.delimiterConceals(delimiter: IElementType): MarkdownLivePreviewSpec.Conceal? {
  val children = childList()
  val leading = children.takeWhile { PsiUtilCore.getElementType(it) == delimiter }
  val trailing = children.takeLastWhile { PsiUtilCore.getElementType(it) == delimiter }
  if (leading.isEmpty() || trailing.isEmpty()) return null
  // The element is malformed or empty, and the two runs are the same tokens.
  if (leading.last().textRange.endOffset > trailing.first().textRange.startOffset) return null
  return inlineConceal(
    TextRange(leading.first().textRange.startOffset, leading.last().textRange.endOffset),
    TextRange(trailing.first().textRange.startOffset, trailing.last().textRange.endOffset),
  )
}

/** Hides `[` and `](destination)` of `[title](destination)`, leaving the title as plain text. */
private fun PsiElement.toInlineLinkSpecs(): MarkdownLivePreviewSpec.Conceal? {
  val linkText = childList().firstOrNull { PsiUtilCore.getElementType(it) == MarkdownElementTypes.LINK_TEXT } ?: return null
  val textChildren = linkText.childList()
  val openBracket = textChildren.firstOrNull() ?: return null
  if (PsiUtilCore.getElementType(openBracket) != MarkdownTokenTypes.LBRACKET) return null
  val closeBracket = textChildren.lastOrNull() ?: return null
  if (PsiUtilCore.getElementType(closeBracket) != MarkdownTokenTypes.RBRACKET) return null
  val end = textRange.endOffset
  // An empty title, or a link whose destination part is missing, has nothing worth hiding.
  if (closeBracket.textRange.startOffset <= openBracket.textRange.endOffset || end <= closeBracket.textRange.startOffset) {
    return null
  }
  return inlineConceal(openBracket.textRange, TextRange(closeBracket.textRange.startOffset, end))
}

/** Hides the markup of a full, collapsed, or shortcut reference link. */
private fun PsiElement.toReferenceLinkSpecs(): MarkdownLivePreviewSpec.Conceal? {
  val children = childList()
  val linkText = children.firstOrNull { PsiUtilCore.getElementType(it) == MarkdownElementTypes.LINK_TEXT }
  val linkLabel = children.firstOrNull { PsiUtilCore.getElementType(it) == MarkdownElementTypes.LINK_LABEL } ?: return null
  val visiblePart = linkText ?: linkLabel
  val visibleChildren = visiblePart.childList()
  val openBracket = visibleChildren.firstOrNull() ?: return null
  if (PsiUtilCore.getElementType(openBracket) != MarkdownTokenTypes.LBRACKET) return null
  val closeBracket = visibleChildren.lastOrNull() ?: return null
  if (PsiUtilCore.getElementType(closeBracket) != MarkdownTokenTypes.RBRACKET) return null
  if (closeBracket.textRange.startOffset <= openBracket.textRange.endOffset) return null

  if (linkText == null) {
    // The parser stores the empty label of a collapsed link as trailing children.
    val end = if (children.size > 1) textRange.endOffset else closeBracket.textRange.endOffset
    return inlineConceal(openBracket.textRange, TextRange(closeBracket.textRange.startOffset, end))
  }

  val end = textRange.endOffset
  if (end <= closeBracket.textRange.startOffset) return null
  return inlineConceal(openBracket.textRange, TextRange(closeBracket.textRange.startOffset, end))
}

/** Hides the angle brackets of wrapped and flat autolinks. */
private fun PsiElement.toAutolinkSpecs(): MarkdownLivePreviewSpec.Conceal? {
  val range = textRange
  val text = text
  if (range.length >= 2 && text.startsWith('<') && text.endsWith('>')) {
    return inlineConceal(
      TextRange(range.startOffset, range.startOffset + 1),
      TextRange(range.endOffset - 1, range.endOffset),
    )
  }
  val openBracket = prevSibling ?: return null
  if (PsiUtilCore.getElementType(openBracket) != MarkdownTokenTypes.LT) return null
  val closeBracket = nextSibling ?: return null
  if (PsiUtilCore.getElementType(closeBracket) != MarkdownTokenTypes.GT) return null
  return MarkdownLivePreviewSpec.Conceal(
    range = TextRange(openBracket.textRange.startOffset, closeBracket.textRange.endOffset).toMarkdownLivePreviewRange(),
    conceals = listOf(openBracket.textRange, closeBracket.textRange).map { it.toMarkdownLivePreviewRange() },
  )
}

private fun PsiElement.inlineConceal(vararg conceals: TextRange): MarkdownLivePreviewSpec.Conceal? {
  val ranges = conceals.filterNot { it.isEmpty }
  return if (ranges.isEmpty()) null else MarkdownLivePreviewSpec.Conceal(
    textRange.toMarkdownLivePreviewRange(),
    ranges.map { it.toMarkdownLivePreviewRange() },
  )
}

private fun PsiElement.childList(): List<PsiElement> = node.getChildren(null).map { it.psi }

private fun String.markdownDestination(): String {
  return if (length >= 2 && first() == '<' && last() == '>') substring(1, length - 1) else this
}

private fun String.isLocalDestination(): Boolean {
  if (isBlank() || startsWith("//")) return false
  val colon = indexOf(':')
  if (colon <= 0) return true
  val hasScheme = take(colon).withIndex().all { (index, char) ->
    if (index == 0) char.isLetter() else char.isLetterOrDigit() || char == '+' || char == '.' || char == '-'
  }
  return !hasScheme || startsWith("file:", ignoreCase = true)
}

private fun String.isBlank(start: Int, end: Int): Boolean {
  for (offset in start until end) {
    if (!this[offset].isWhitespace()) return false
  }
  return true
}

private fun String.firstLineBreak(): Int {
  val lineFeed = indexOf('\n')
  val carriageReturn = indexOf('\r')
  return when {
    lineFeed < 0 -> carriageReturn
    carriageReturn < 0 -> lineFeed
    else -> minOf(lineFeed, carriageReturn)
  }
}
