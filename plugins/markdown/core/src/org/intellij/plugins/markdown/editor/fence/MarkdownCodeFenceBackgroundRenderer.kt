// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.fence

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.CustomHighlighterOrder
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.JBUIScale
import org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D

@ApiStatus.Internal
val MARKDOWN_CODE_FENCE_HIGHLIGHTER_ID: String = "IJ." + MarkdownHighlighterColors.CODE_FENCE_BACKGROUND.externalName

@ApiStatus.Internal
val MARKDOWN_CODE_SPAN_HIGHLIGHTER_ID: String = "IJ." + MarkdownHighlighterColors.CODE_SPAN_BACKGROUND.externalName

@ApiStatus.Internal
class MarkdownCodeFenceBackgroundRenderer : CustomHighlighterRenderer {
  override fun getOrder(): CustomHighlighterOrder = CustomHighlighterOrder.BEFORE_BACKGROUND

  override fun paint(editor: Editor, highlighter: RangeHighlighter, graphics: Graphics) {
    val color = backgroundColor(editor) ?: return
    // offsetToXY maps every offset of a collapsed region to the line of its placeholder. A fence that
    // a region hides completely therefore paints as a single row over that placeholder, so skip it. A
    // fence that a region hides only in part keeps its paint, because such a placeholder is itself a
    // line of the code. An expanded region hides nothing, so it needs no check.
    val collapsed = editor.foldingModel.getCollapsedRegionAtOffset(highlighter.startOffset)
    if (collapsed != null && collapsed.endOffset >= highlighter.endOffset) return
    val start = editor.offsetToXY(highlighter.startOffset)
    val width = visibleWidth(editor) - start.x
    if (width <= 0) return
    val bottom = editor.offsetToXY(highlighter.endOffset).y + editor.lineHeight
    graphics.color = color
    paintCodeBackground(editor, graphics, start.x, start.y, width, bottom - start.y)
  }

  private fun backgroundColor(editor: Editor): Color? {
    val scheme = editor.colorsScheme
    scheme.getAttributes(MarkdownHighlighterColors.CODE_FENCE)?.backgroundColor?.let { return it }
    val injectedKey = EditorColors.createInjectedLanguageFragmentKey(MarkdownLanguage.INSTANCE)
    return scheme.getAttributes(injectedKey)?.backgroundColor
  }

  private fun visibleWidth(editor: Editor): Int {
    val visibleArea = editor.scrollingModel.visibleArea
    val scrollPane = (editor as? EditorEx)?.scrollPane
    val scrollbarWidth = if (scrollPane != null && editor.verticalScrollbarOrientation != EditorEx.VERTICAL_SCROLLBAR_LEFT) scrollPane.verticalScrollBar.width else 0
    return visibleArea.x + visibleArea.width - scrollbarWidth - JBUIScale.scale(RIGHT_GAP)
  }

  private companion object {
    /** The space between the background and the vertical scrollbar, in unscaled pixels. */
    private const val RIGHT_GAP = 12
  }
}

/**
 * Paints the background of one code span behind the content between its backtick runs.
 * The painter fills one rectangle per visual line, so a wrapped span keeps the shape of its text.
 * The background takes the color of [MarkdownHighlighterColors.CODE_SPAN].
 */
@ApiStatus.Internal
class MarkdownCodeSpanBackgroundRenderer : CustomHighlighterRenderer {
  override fun getOrder(): CustomHighlighterOrder = CustomHighlighterOrder.BEFORE_BACKGROUND

  override fun paint(editor: Editor, highlighter: RangeHighlighter, graphics: Graphics) {
    val color = editor.colorsScheme.getAttributes(MarkdownHighlighterColors.CODE_SPAN)?.backgroundColor ?: return
    val start = highlighter.startOffset
    val end = highlighter.endOffset
    // A heading of the live preview is one collapsed region, and it renders its code spans itself.
    val collapsed = editor.foldingModel.getCollapsedRegionAtOffset(start)
    if (collapsed != null && collapsed.endOffset >= end) return
    val document = editor.document
    graphics.color = color
    var segmentStart = start
    while (segmentStart < end) {
      val visualLine = editor.offsetToVisualLine(segmentStart, false)
      val lineEnd = document.getLineEndOffset(document.getLineNumber(segmentStart))
      val nextVisualLineStart = editor.visualPositionToOffset(VisualPosition(visualLine + 1, 0))
      val segmentEnd = minOf(end, lineEnd, nextVisualLineStart)
      val x1 = editor.offsetToXY(segmentStart, true, false).x
      val x2 = editor.offsetToXY(segmentEnd, false, true).x
      if (x2 > x1) paintCodeBackground(editor, graphics, x1, editor.visualLineToY(visualLine), x2 - x1, editor.lineHeight)
      // A segment that ends at a line end skips the line break. The other ends are a soft wrap or the end of the span.
      segmentStart = if (segmentEnd == lineEnd) segmentEnd + 1 else segmentEnd
    }
  }
}

/**
 * Fills a rectangle with the corners of the editor selection: a quadratic curve with a radius of a sixth of the line height.
 * A narrow rectangle gets a smaller radius, so the corners do not cross.
 */
private fun paintCodeBackground(editor: Editor, graphics: Graphics, x: Int, y: Int, width: Int, height: Int) {
  val radius = minOf(editor.lineHeight / 6.0, width / 2.0, height / 2.0)
  val left = x.toDouble()
  val top = y.toDouble()
  val right = left + width
  val bottom = top + height
  val path = Path2D.Double().apply {
    moveTo(left, top + radius)
    quadTo(left, top, left + radius, top)
    lineTo(right - radius, top)
    quadTo(right, top, right, top + radius)
    lineTo(right, bottom - radius)
    quadTo(right, bottom, right - radius, bottom)
    lineTo(left + radius, bottom)
    quadTo(left, bottom, left, bottom - radius)
    closePath()
  }
  val graphics2D = graphics as Graphics2D
  PaintUtil.paintWithAA(graphics2D, RenderingHints.VALUE_ANTIALIAS_ON) { graphics2D.fill(path) }
}
