// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.fence

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.CustomHighlighterOrder
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.text.CharArrayUtil
import org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Path2D

private val RENDERERS_BY_ATTRIBUTE_ID = MarkdownBackgroundRenderer.entries.associateBy { "IJ." + it.highlighterKey.externalName }

/** Find the renderer of a background highlighter that a split-mode frontend gets with [attributeId]. */
@ApiStatus.Internal
fun findMarkdownBackgroundRenderer(attributeId: String): CustomHighlighterRenderer? = RENDERERS_BY_ATTRIBUTE_ID[attributeId]

/** Paints the rounded background over the visual lines of a highlighter. */
@ApiStatus.Internal
enum class MarkdownBackgroundRenderer(
  internal val highlighterKey: TextAttributesKey,
  private vararg val colorKeys: TextAttributesKey,
) : CustomHighlighterRenderer {
  CODE_FENCE(
    MarkdownHighlighterColors.CODE_FENCE_BACKGROUND,
    MarkdownHighlighterColors.CODE_FENCE,
    EditorColors.createInjectedLanguageFragmentKey(MarkdownLanguage.INSTANCE),
  ),
  BLOCK_QUOTE(MarkdownHighlighterColors.BLOCK_QUOTE_BACKGROUND, MarkdownHighlighterColors.BLOCK_QUOTE),
  CODE_SPAN(MarkdownHighlighterColors.CODE_SPAN_BACKGROUND, MarkdownHighlighterColors.CODE_SPAN);

  override fun getOrder(): CustomHighlighterOrder = CustomHighlighterOrder.BEFORE_BACKGROUND

  override fun paint(editor: Editor, highlighter: RangeHighlighter, graphics: Graphics) {
    val color = colorKeys.firstNotNullOfOrNull { editor.colorsScheme.getAttributes(it)?.backgroundColor } ?: return
    if (isHiddenByFold(editor, highlighter)) return
    graphics.color = color
    val lines = visualLines(editor, highlighter, graphics.clipBounds)
    val startX = editor.offsetToXY(highlighter.startOffset).x
    when (this) {
      CODE_FENCE -> {
        val rightEdge = getRightVisibleEdge(editor)
        fillRows(editor, graphics, lines.map { Row(startX, it.top, it.bottom) }, rightEdge, roundLeft = true)
      }
      BLOCK_QUOTE -> {
        val rows = lines.map { Row(minOf(startX, getTextStart(editor, it)), it.top, it.bottom) }
        fillRows(editor, graphics, rows, getRightVisibleEdge(editor), roundLeft = false)
      }
      CODE_SPAN -> {
        for (line in lines) {
          val left = editor.offsetToXY(line.start, true, false).x
          val right = editor.offsetToXY(line.end, false, true).x
          if (right > left) {
            val rows = listOf(Row(left, line.top, line.top + editor.lineHeight))
            fillRows(editor, graphics, rows, right, roundLeft = true)
          }
        }
      }
    }
  }
}

private fun isHiddenByFold(editor: Editor, highlighter: RangeHighlighter): Boolean {
  val collapsed = editor.foldingModel.getCollapsedRegionAtOffset(highlighter.startOffset)
  return collapsed != null && collapsed.endOffset >= highlighter.endOffset
}

/** The part of a highlighter on one visual line: the offsets from [start] to [end], and the y range from [top] to [bottom]. */
private class VisualLine(val start: Int, val end: Int, val top: Int, val bottom: Int)

/** One band of a background: the visual lines from [top] to [bottom] whose left side is [left]. */
private class Row(val left: Int, val top: Int, val bottom: Int)

/**
 * The visual lines of [highlighter]. The lines stop one visual line outside [clip], so the corners of a cut shape stay out of sight.
 * The part of a line stops at a soft wrap or at the line break.
 * A line ends at the top of the next line, so a block inlay between two lines belongs to the line above it.
 */
private fun visualLines(editor: Editor, highlighter: RangeHighlighter, clip: Rectangle?): List<VisualLine> {
  val start = highlighter.startOffset
  val end = highlighter.endOffset
  val document = editor.document
  val firstLine = editor.offsetToVisualLine(start, false)
  val lastLine = editor.offsetToVisualLine(end, true)
  val fromLine = if (clip == null) firstLine else maxOf(firstLine, editor.yToVisualLine(clip.y) - 1)
  val toLine = if (clip == null) lastLine else minOf(lastLine, editor.yToVisualLine(clip.y + clip.height) + 1)
  return (fromLine..toLine).map { line ->
    val lineStart = maxOf(start, editor.visualPositionToOffset(VisualPosition(line, 0)))
    val nextLineStart = editor.visualPositionToOffset(VisualPosition(line + 1, 0))
    val lineEnd = minOf(end, document.getLineEndOffset(document.getLineNumber(lineStart)), nextLineStart)
    val bottom = if (line < lastLine) editor.visualLineToY(line + 1) else editor.visualLineToYRange(line)[1]
    VisualLine(lineStart, lineEnd, editor.visualLineToY(line), bottom)
  }
}

private fun getTextStart(editor: Editor, line: VisualLine): Int =
  editor.offsetToXY(CharArrayUtil.shiftForward(editor.document.charsSequence, line.start, line.end, " \t"), true, false).x

private fun getRightVisibleEdge(editor: Editor): Int {
  val visibleArea = editor.scrollingModel.visibleArea
  val scrollPane = (editor as? EditorEx)?.scrollPane
  val scrollbarWidth = if (scrollPane != null && editor.verticalScrollbarOrientation != EditorEx.VERTICAL_SCROLLBAR_LEFT) scrollPane.verticalScrollBar.width else 0
  return visibleArea.x + visibleArea.width - scrollbarWidth - JBUIScale.scale(RIGHT_GAP)
}

/** The space between the background and the vertical scrollbar, in unscaled pixels. */
private const val RIGHT_GAP = 12
/**
 * Fills [rows] up to [right] as one shape. The rows follow each other from top to bottom, and the left side steps from row to row.
 * The right corners are round. The outer left corners are round only when [roundLeft] is true.
 * A corner is a quadratic curve with a radius of a sixth of the line height, like the corners of the editor selection.
 * A narrow shape gets a smaller radius, so the corners do not cross.
 */
private fun fillRows(editor: Editor, graphics: Graphics, rows: List<Row>, right: Int, roundLeft: Boolean) {
  if (rows.none { it.left < right }) return
  fun left(row: Row): Double = minOf(row.left, right).toDouble()
  val first = rows.first()
  val last = rows.last()
  val end = right.toDouble()
  val top = first.top.toDouble()
  val bottom = last.bottom.toDouble()
  val radius = minOf(editor.lineHeight / 6.0, (end - left(first)) / 2.0, (end - left(last)) / 2.0, (bottom - top) / 2.0)
  val leftRadius = if (roundLeft) radius else 0.0
  val path = Path2D.Double().apply {
    moveTo(left(first), top + leftRadius)
    quadTo(left(first), top, left(first) + leftRadius, top)
    lineTo(end - radius, top)
    quadTo(end, top, end, top + radius)
    lineTo(end, bottom - radius)
    quadTo(end, bottom, end - radius, bottom)
    lineTo(left(last) + leftRadius, bottom)
    quadTo(left(last), bottom, left(last), bottom - leftRadius)
    // Go up the left side. A step connects each row to the row above it.
    for (index in rows.lastIndex downTo 1) {
      val y = rows[index].top.toDouble()
      lineTo(left(rows[index]), y)
      lineTo(left(rows[index - 1]), y)
    }
    closePath()
  }
  val graphics2D = graphics as Graphics2D
  PaintUtil.paintWithAA(graphics2D, RenderingHints.VALUE_ANTIALIAS_ON) { graphics2D.fill(path) }
}
