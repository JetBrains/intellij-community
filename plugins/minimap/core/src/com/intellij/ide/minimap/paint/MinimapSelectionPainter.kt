// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.paint

import com.intellij.ide.minimap.layout.MinimapLayoutMetrics
import com.intellij.ide.minimap.render.MinimapRenderContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import kotlin.math.max
import kotlin.math.min

class MinimapSelectionPainter(private val editor: Editor) {
  fun paint(graphics: Graphics2D, context: MinimapRenderContext, metrics: MinimapLayoutMetrics?) {
    val metrics = metrics ?: return
    if (metrics.lineCount <= 0) return
    if (context.panelWidth <= 0 || context.panelHeight <= 0) return

    val document = editor.document
    if (document.textLength <= 0) return

    val carets = editor.caretModel.allCarets
    if (carets.isEmpty()) return

    val baseLineHeight = metrics.baseLineHeight
    if (baseLineHeight <= 0.0) return

    val lineProjection = context.lineProjection
    val lineHeight = baseLineHeight.coerceAtLeast(1.0)
    val contentStartX = metrics.contentStartX
    val contentEndX = contentStartX + metrics.contentWidth
    val maxYOffset = context.panelHeight.toDouble()
    val pxPerColumn = metrics.pxPerColumn
    val style = selectionRenderStyle()

    val selLines = ArrayList<SelectionLine>()
    for ((caretIdx, caret) in carets.withIndex()) {
      if (!caret.hasSelection()) continue
      val textLength = document.textLength
      val selectionStart = caret.selectionStart.coerceIn(0, textLength)
      val selectionEnd = caret.selectionEnd.coerceIn(0, textLength)
      if (selectionEnd <= selectionStart) continue

      val endOffsetInclusive = (selectionEnd - 1).coerceAtLeast(selectionStart)
      val startLine = document.getLineNumber(selectionStart)
      val endLine = document.getLineNumber(endOffsetInclusive)

      for (line in startLine..endLine) {
        val projectedLine = lineProjection.logicalToVisibleProjectedLine(line) ?: continue
        val y = projectedLine * baseLineHeight - context.geometry.areaStart
        if (y > maxYOffset || y + lineHeight < 0.0) continue

        val lineStartOffset = document.getLineStartOffset(line)
        val lineEndOffset = document.getLineEndOffset(line)
        val segmentStart = if (line == startLine) selectionStart else lineStartOffset
        val segmentEndExclusive = if (line == endLine) selectionEnd else lineEndOffset

        val tokenRect = if (segmentEndExclusive > segmentStart) {
          val startColumn = (segmentStart - lineStartOffset).coerceAtLeast(0)
          val endColumn = (segmentEndExclusive - lineStartOffset).coerceAtLeast(startColumn + 1)
          val x = if (pxPerColumn > 0.0) contentStartX + startColumn * pxPerColumn else contentStartX
          val width = if (pxPerColumn > 0.0) ((endColumn - startColumn) * pxPerColumn).coerceAtLeast(1.0) else metrics.contentWidth
          clampSelectionRect(projectedLine, x, y, width, lineHeight, contentStartX, contentEndX, style.horizontalPaddingPx)
        }
        else null

        selLines += SelectionLine(caretIdx, projectedLine, y, tokenRect)
      }
    }

    if (selLines.isEmpty()) return

    val oldComposite = graphics.composite
    graphics.color = style.color

    graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, style.lineBgAlpha)
    for (sl in selLines) {
      graphics.fill(Rectangle2D.Double(0.0, sl.y, context.panelWidth.toDouble(), lineHeight))
    }

    graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, style.alpha)
    var prevCaretIdx = -1
    var prevLine: SelectionLine? = null
    for (sl in selLines) {
      if (sl.caretIdx != prevCaretIdx) {
        prevCaretIdx = sl.caretIdx
        prevLine = null
      }
      val rect = sl.tokenRect ?: run { prevLine = sl; continue }
      prevLine?.tokenRect?.let { prevRect ->
        if (prevLine.projectedLine + 1 == sl.projectedLine) {
          fillConnector(graphics, prevRect, rect, contentStartX, contentEndX, maxYOffset, style.connectorHeightPx)
        }
      }
      graphics.fill(Rectangle2D.Double(rect.x, rect.y, rect.width, rect.height))
      prevLine = sl
    }

    graphics.composite = oldComposite
  }

  private fun selectionRenderStyle(): SelectionRenderStyle {
    val colorsScheme = editor.colorsScheme
    val hasFocus = editor.contentComponent.hasFocus()
    val activeSelection = colorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR)
    val inactiveSelection = colorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR_INACTIVE)
    val selectionFallback = colorsScheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR)
                            ?: colorsScheme.getColor(EditorColors.CARET_COLOR)
                            ?: colorsScheme.defaultForeground

    val color = when {
      hasFocus -> activeSelection ?: inactiveSelection ?: selectionFallback
      else -> inactiveSelection ?: activeSelection ?: selectionFallback
    }

    return SelectionRenderStyle(
      alpha = SELECTION_ALPHA,
      lineBgAlpha = SELECTION_LINE_BG_ALPHA,
      color = color,
      horizontalPaddingPx = SELECTION_HORIZONTAL_PADDING_PX,
      connectorHeightPx = SELECTION_CONNECTOR_HEIGHT_PX
    )
  }

  private fun clampSelectionRect(line: Int,
                                 x: Double,
                                 y: Double,
                                 width: Double,
                                 height: Double,
                                 minX: Double,
                                 maxX: Double,
                                 horizontalPaddingPx: Double): SelectionRect? {
    val paddedX = (x - horizontalPaddingPx).coerceIn(minX, maxX)
    if (paddedX >= maxX) return null

    val maxDrawableWidth = (maxX - paddedX).coerceAtLeast(1.0)
    val paddedWidth = (width + horizontalPaddingPx * 2.0).coerceAtLeast(1.0)
    val clampedWidth = paddedWidth.coerceAtMost(maxDrawableWidth).coerceAtLeast(1.0)
    return SelectionRect(line = line, x = paddedX, y = y, width = clampedWidth, height = height)
  }

  private fun fillConnector(graphics: Graphics2D,
                            previousRect: SelectionRect,
                            currentRect: SelectionRect,
                            minX: Double,
                            maxX: Double,
                            maxYOffset: Double,
                            connectorHeightPx: Double) {
    if (connectorHeightPx <= 0.0) return

    val connectorY = currentRect.y - connectorHeightPx / 2.0
    if (connectorY > maxYOffset || connectorY + connectorHeightPx < 0.0) return

    val connectorStartX = min(previousRect.x, currentRect.x).coerceIn(minX, maxX)
    val connectorEndX = max(previousRect.x + previousRect.width, currentRect.x + currentRect.width).coerceIn(connectorStartX, maxX)
    val connectorWidth = (connectorEndX - connectorStartX).coerceAtLeast(1.0)
    graphics.fill(Rectangle2D.Double(connectorStartX, connectorY, connectorWidth, connectorHeightPx))
  }

  private data class SelectionLine(
    val caretIdx: Int,
    val projectedLine: Int,
    val y: Double,
    val tokenRect: SelectionRect?,
  )

  private data class SelectionRect(
    val line: Int,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double
  )

  private data class SelectionRenderStyle(
    val alpha: Float,
    val lineBgAlpha: Float,
    val color: Color,
    val horizontalPaddingPx: Double,
    val connectorHeightPx: Double
  )

  companion object {
    private const val SELECTION_ALPHA: Float = 0.9f
    private const val SELECTION_LINE_BG_ALPHA: Float = 0.15f
    private const val SELECTION_HORIZONTAL_PADDING_PX: Double = 0.5
    private const val SELECTION_CONNECTOR_HEIGHT_PX: Double = 1.0
  }
}
