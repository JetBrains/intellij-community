package org.intellij.plugins.markdown.extensions.common.highlighter

import com.intellij.lang.Language
import com.intellij.markdown.utils.lang.HtmlSyntaxHighlighter
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.util.text.TextRangeUtil
import com.intellij.util.text.splitToTextRanges
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.intellij.plugins.markdown.lang.psi.util.textRange
import org.intellij.plugins.markdown.ui.preview.html.DefaultCodeFenceGeneratingProvider
import org.intellij.plugins.markdown.ui.preview.html.children
import java.awt.Color
import kotlin.math.max
import kotlin.math.min

private inline fun collectHighlights(language: Language, text: String, crossinline consumer: (String, IntRange, Color) -> Unit) {
  HtmlSyntaxHighlighter.parseContent(project = null, language, text) { content, range, color ->
    if (color != null) {
      consumer.invoke(content, range, color)
    }
  }
}

internal data class HighlightedRange(
  val range: TextRange,
  val color: Color
): Comparable<HighlightedRange> {
  override fun compareTo(other: HighlightedRange): Int {
    return TextRangeUtil.RANGE_COMPARATOR.compare(range, other.range)
  }
}

internal fun collectHighlightedChunks(language: Language, text: String): List<HighlightedRange> {
  return buildList {
    collectHighlights(language, text) { _, range, color ->
      add(HighlightedRange(TextRange(range.first, range.last), color))
    }
  }
}

internal fun buildHighlightedFenceContent(
  text: String,
  highlightedRanges: List<HighlightedRange>,
  node: ASTNode,
  useAbsoluteOffsets: Boolean,
  additionalLineProcessor: ((String) -> String)? = null
): String {
  val contentBaseOffset = DefaultCodeFenceGeneratingProvider.calculateCodeFenceContentBaseOffset(node)
  check(contentBaseOffset >= node.startOffset) { "Content base offset should not be before fence start" }
  val builder = StringBuilder()
  val fenceStartOffset = when {
    useAbsoluteOffsets -> node.startOffset
    else -> 0
  }
  val contentOffset = when {
    useAbsoluteOffsets -> contentBaseOffset
    else -> contentBaseOffset - node.startOffset
  }
  check(contentOffset >= fenceStartOffset) { "Content offset should not be before fence start" }
  builder.appendChunk(createSpan(TextRange(fenceStartOffset, contentOffset)))
  processLines(builder, highlightedRanges, text, contentOffset, additionalLineProcessor)
  val closingDelimiter = node.children().firstOrNull { it.hasType(MarkdownTokenTypes.CODE_FENCE_END) }
  if (closingDelimiter != null) {
    val range = closingDelimiter.textRange
    val actualRange = when {
      useAbsoluteOffsets -> range
      else -> range.shiftLeft(node.startOffset)
    }
    builder.appendChunk(createSpan(actualRange))
  }
  return builder.toString()
}

@Suppress("UsePropertyAccessSyntax", "NAME_SHADOWING")
private fun processLines(
  builder: StringBuilder,
  highlightedRanges: List<HighlightedRange>,
  text: String,
  contentOffset: Int,
  additionalLineProcessor: ((String) -> String)? = null
) {
  for (lineRange in text.lineRanges(includeDelimiter = true)) {
    val ranges = highlightedRanges.filter { (range, _) -> lineRange.intersects(range) }
    if (ranges.isEmpty()) {
      val span = createSpan(lineRange.shiftRight(contentOffset), lineRange.substring(text))
      builder.appendChunk(span)
      continue
    }
    var left = lineRange.startOffset
    for ((range, color) in ranges) {
      val start = max(left, range.startOffset)
      val end = min(lineRange.endOffset, range.endOffset)
      if (start != left) {
        val range = TextRange(left, start)
        val span = createSpan(range.shiftRight(contentOffset), range.substring(text))
        builder.appendChunk(span)
      }
      if (end - start > 0) {
        val range = TextRange(start, end)
        if (!range.isEmpty()) {
          val span = createSpan(range.shiftRight(contentOffset), range.substring(text), color)
          builder.appendChunk(span)
        }
      }
      left = end
    }
    if (left != lineRange.endOffset) {
      val range = TextRange(left, lineRange.endOffset)
      val span = createSpan(range.shiftRight(contentOffset), range.substring(text))
      builder.appendChunk(span)
    }
    if (additionalLineProcessor != null) {
      val processed = additionalLineProcessor.invoke(lineRange.substring(text))
      builder.append(processed)
    }
  }
}

private fun CharSequence.lineRanges(includeDelimiter: Boolean = true): Sequence<TextRange> {
  return splitToTextRanges(this, delimiter = "\n", includeDelimiter = includeDelimiter)
}

private fun createSpan(range: TextRange, text: String = "", color: Color? = null): HtmlChunk {
  val span = HtmlChunk.span().sourceRange(range)
  if (text.isEmpty() && color == null) {
    return span
  }
  return span.escapedText(text).color(color)
}

private fun StringBuilder.appendChunk(chunk: HtmlChunk): StringBuilder {
  chunk.appendTo(this)
  return this
}

private fun HtmlChunk.Element.sourceRange(range: TextRange): HtmlChunk.Element {
  return attr(HtmlGenerator.SRC_ATTRIBUTE_NAME, "${range.startOffset}..${range.endOffset}")
}

private fun HtmlChunk.Element.escapedText(text: String): HtmlChunk.Element {
  @Suppress("HardCodedStringLiteral")
  return addRaw(DefaultCodeFenceGeneratingProvider.escape(text))
}

private fun HtmlChunk.Element.color(color: Color?): HtmlChunk.Element {
  if (color == null) {
    return this
  }
  val value = ColorUtil.toHtmlColor(color)
  return style("color: $value;")
}
