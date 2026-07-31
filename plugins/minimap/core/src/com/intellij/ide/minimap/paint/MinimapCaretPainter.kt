// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.paint

import com.intellij.ide.minimap.MinimapPanel
import com.intellij.ide.minimap.geometry.MinimapLineGeometryUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Rectangle2D
import kotlin.math.roundToInt

class MinimapCaretPainter(private val editor: Editor, private val panel: MinimapPanel) {
  fun paint(graphics: Graphics2D) {
    val rect = caretRectForOffset(editor.caretModel.primaryCaret.offset) ?: return
    val oldComposite = graphics.composite
    graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, CARET_ALPHA)
    graphics.color = caretColor()
    graphics.fill(Rectangle2D.Double(rect.x.toDouble(), rect.y.toDouble(), rect.width.toDouble(), rect.height.toDouble()))
    graphics.composite = oldComposite
  }

  fun caretRectForOffset(offset: Int): Rectangle? {
    val snapshot = panel.currentSnapshot() ?: return null
    val context = snapshot.context
    val lineProjection = context.lineProjection
    val lineCount = lineProjection.projectedLineCount
    val geometry = context.geometry

    if (offset < 0 || lineCount == 0 || geometry.minimapHeight <= 0 || context.panelWidth <= 0) {
      return null
    }

    val baseLineHeight = MinimapLineGeometryUtil.baseLineHeight(lineCount, geometry.minimapHeight)

    val document = editor.document
    val safeOffset = offset.coerceIn(0, document.textLength)
    val lineNumber = document.getLineNumber(safeOffset)
    val projectedLine = lineProjection.logicalToProjectedLine(lineNumber) ?: return null

    val lineStartY = MinimapLineGeometryUtil.lineTop(projectedLine, baseLineHeight) - geometry.areaStart
    val lineHeight = baseLineHeight.coerceAtLeast(1.0)

    if (lineHeight <= 0.0 || lineStartY > context.panelHeight || lineStartY + lineHeight < 0) {
      return null
    }

    return Rectangle(
      0,
      lineStartY.roundToInt(),
      context.panelWidth,
      lineHeight.roundToInt().coerceAtLeast(1)
    )
  }

  private fun caretColor(): Color {
    val scheme = editor.colorsScheme
    return scheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR)
           ?: scheme.getColor(EditorColors.CARET_COLOR)
           ?: scheme.defaultForeground
  }

  companion object {
    private const val CARET_ALPHA: Float = 0.6f
  }
}
