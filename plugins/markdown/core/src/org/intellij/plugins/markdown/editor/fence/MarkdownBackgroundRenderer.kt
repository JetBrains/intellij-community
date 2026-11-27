// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.fence

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.CustomHighlighterOrder
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.DocumentUtil
import com.intellij.util.text.CharArrayUtil
import org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.Path2D
import kotlin.math.roundToInt

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
    val color = getColor(editor) ?: return
    if (isHiddenByFold(editor, highlighter)) return
    graphics.color = color
    when (this) {
      CODE_FENCE -> paintCodeFence(editor, highlighter, graphics, editor.offsetToXY(highlighter.startOffset).x)
      BLOCK_QUOTE -> {
        val startX = editor.offsetToXY(highlighter.startOffset).x
        val rows = visualLines(editor, highlighter.startOffset, highlighter.endOffset, graphics.clipBounds)
          .map { Row(minOf(startX, getTextStart(editor, it)), it.top, it.bottom) }
        fillRows(editor, graphics, withAlertPadding(editor, rows, highlighter), getRightVisibleEdge(editor), roundLeft = false)
      }
      CODE_SPAN -> {
        for (line in visualLines(editor, highlighter.startOffset, highlighter.endOffset, graphics.clipBounds)) {
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

  internal fun getColor(editor: Editor): Color? = colorKeys.firstNotNullOfOrNull { editor.colorsScheme.getAttributes(it)?.backgroundColor }
}

/**
 * Reserves vertical space of an alert in a block inlay: above its first line, below its last line, or below its title line.
 * The block quote background and the block quote rule cover the space above the first line and below the last line.
 */
@ApiStatus.Internal
class MarkdownAlertPaddingRenderer(private val editor: Editor, private val lineHeightRatio: Double) : EditorCustomElementRenderer {
  override fun calcWidthInPixels(inlay: Inlay<*>): Int = 0

  override fun calcHeightInPixels(inlay: Inlay<*>): Int = (editor.lineHeight * lineHeightRatio).roundToInt().coerceAtLeast(1)
}

/** The top of the alert padding above the line of an offset from [startOffset] to [endOffset], or null when there is none. */
@ApiStatus.Internal
fun getAlertPaddingTop(editor: Editor, startOffset: Int, endOffset: Int): Int? =
  getAlertPaddingBounds(editor, startOffset, endOffset, Inlay.Placement.ABOVE_LINE).minOfOrNull { it.y }

/** The bottom of the alert padding below the line of an offset from [startOffset] to [endOffset], or null when there is none. */
@ApiStatus.Internal
fun getAlertPaddingBottom(editor: Editor, startOffset: Int, endOffset: Int): Int? =
  getAlertPaddingBounds(editor, startOffset, endOffset, Inlay.Placement.BELOW_LINE).maxOfOrNull { it.y + it.height }

private fun getAlertPaddingBounds(editor: Editor, startOffset: Int, endOffset: Int, placement: Inlay.Placement): List<Rectangle> =
  editor.inlayModel.getBlockElementsInRange(startOffset, endOffset, MarkdownAlertPaddingRenderer::class.java)
    .filter { it.placement == placement }
    .mapNotNull { it.bounds }

/** Stretches the first row over the alert padding above the first line, and the last row over the padding below the last line. */
private fun withAlertPadding(editor: Editor, rows: List<Row>, highlighter: RangeHighlighter): List<Row> {
  if (rows.isEmpty()) return rows
  val document = editor.document
  val start = highlighter.startOffset
  val end = highlighter.endOffset
  val top = getAlertPaddingTop(editor, start, DocumentUtil.getLineEndOffset(start, document))
  val bottom = getAlertPaddingBottom(editor, DocumentUtil.getLineStartOffset(end, document), end)
  if (top == null && bottom == null) return rows
  val padded = rows.toMutableList()
  if (top != null) padded[0] = Row(padded[0].left, minOf(top, padded[0].top), padded[0].bottom)
  if (bottom != null) padded[padded.lastIndex] = Row(padded.last().left, padded.last().top, maxOf(bottom, padded.last().bottom))
  return padded
}

@ApiStatus.Internal
fun paintBackground(editor: Editor, graphics: Graphics, left: Int, top: Int, bottom: Int): Shape? {
  val shape = rowsShape(editor, listOf(Row(left, top, bottom)), getRightVisibleEdge(editor), roundLeft = true) ?: return null
  val color = MarkdownBackgroundRenderer.CODE_FENCE.getColor(editor)
  if (color != null) {
    graphics.color = color
    fill(graphics, shape)
  }
  return shape
}

/**
 * Fills the lines of the fence from the opening marker line to the closing marker line.
 * A folded marker line stays in the background, so the code has one line above it and one below it.
 */
private fun paintCodeFence(editor: Editor, highlighter: RangeHighlighter, graphics: Graphics, startX: Int) {
  val rows = visualLines(editor, highlighter.startOffset, highlighter.endOffset, graphics.clipBounds)
    .map { Row(startX, it.top, it.bottom) }
  fillRows(editor, graphics, rows, getRightVisibleEdge(editor), roundLeft = true)
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
 * The visual lines from [start] to [end]. The lines stop one visual line outside [clip], so the corners of a cut shape stay out of sight.
 * The part of a line stops at a soft wrap or at the line break.
 * A line ends at the top of the next line, so a block inlay between two lines belongs to the line above it.
 */
private fun visualLines(editor: Editor, start: Int, end: Int, clip: Rectangle?): List<VisualLine> {
  val document = editor.document
  val firstLine = editor.offsetToVisualLine(start, false)
  val lastLine = editor.offsetToVisualLine(end, true)
  val fromLine = if (clip == null || editor.visualLineToY(firstLine) >= clip.y) firstLine
                 else maxOf(firstLine, editor.yToVisualLine(clip.y) - 1)
  val toLine = if (clip == null || editor.visualLineToY(lastLine) <= clip.y + clip.height) lastLine
               else minOf(lastLine, editor.yToVisualLine(clip.y + clip.height) + 1)
  return (fromLine..toLine).map { line ->
    val lineStart = maxOf(start, visualLineStart(editor, line))
    val nextLineStart = visualLineStart(editor, line + 1)
    val lineEnd = minOf(end, document.getLineEndOffset(document.getLineNumber(lineStart)), nextLineStart)
    val bottom = if (line < lastLine) editor.visualLineToY(line + 1) else editor.visualLineToYRange(line)[1]
    VisualLine(lineStart, lineEnd, editor.visualLineToY(line), bottom)
  }
}

private fun visualLineStart(editor: Editor, line: Int): Int =
  if (editor is EditorImpl) editor.visualLineStartOffset(line) else editor.visualPositionToOffset(VisualPosition(line, 0))

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
  val shape = rowsShape(editor, rows, right, roundLeft) ?: return
  fill(graphics, shape)
}

private fun rowsShape(editor: Editor, rows: List<Row>, right: Int, roundLeft: Boolean): Path2D? {
  if (rows.none { it.left < right }) return null
  fun left(row: Row): Double = minOf(row.left, right).toDouble()
  val first = rows.first()
  val last = rows.last()
  val end = right.toDouble()
  val top = first.top.toDouble()
  val bottom = last.bottom.toDouble()
  val radius = minOf(editor.lineHeight / 6.0, (end - left(first)) / 2.0, (end - left(last)) / 2.0, (bottom - top) / 2.0)
  val leftRadius = if (roundLeft) radius else 0.0
  return Path2D.Double().apply {
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
}

private fun fill(graphics: Graphics, shape: Shape) {
  val graphics2D = graphics as Graphics2D
  PaintUtil.paintWithAA(graphics2D, RenderingHints.VALUE_ANTIALIAS_ON) { graphics2D.fill(shape) }
}
