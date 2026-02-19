// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils.toFloatAndScale
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D

/**
 * Paints the left part of the rounded block frame in the gutter area.
 * Also, can paint the border if [strokeBackgroundKey] is specified and [strokeWidth] is greater than 0.
 */
internal class TerminalBlockLeftAreaRenderer(
  private val backgroundKey: ColorKey,
  private val failoverBackgroundKey: ColorKey? = null,
  private val strokeBackgroundKey: ColorKey? = null,
  private val strokeWidth: Int = 0
) : LineMarkerRenderer {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    val topIns = toFloatAndScale(TerminalUi.blockTopInset)
    val blocksGap = toFloatAndScale(TerminalUi.blocksGap)
    val width = toFloatAndScale(TerminalUi.blockLeftInset)
    val arc = toFloatAndScale(TerminalUi.blockArc)
    val selectionGap = JBUI.scale(TerminalUi.blockSelectionSeparatorGap)

    val gutterWidth = (editor as EditorEx).gutterComponentEx.width
    // r.height includes the height of the block text and the height of the inlays below the last line
    // so, to get the full block height, we need to add top inset and remove the gap between blocks
    val rect = Rectangle2D.Float(gutterWidth - width, r.y - topIns + selectionGap, width, r.height + topIns - blocksGap - selectionGap * 2)

    // from right bottom corner to the right top corner
    val outerPath = Path2D.Float(Path2D.WIND_EVEN_ODD).apply {
      moveTo(rect.x + rect.width, rect.y + rect.height)
      lineTo(rect.x + arc, rect.y + rect.height)
      quadTo(rect.x, rect.y + rect.height, rect.x, rect.y + rect.height - arc)
      lineTo(rect.x, rect.y + arc)
      quadTo(rect.x, rect.y, rect.x + arc, rect.y)
      lineTo(rect.x + rect.width, rect.y)
    }

    val path = Path2D.Float(Path2D.WIND_EVEN_ODD).apply {
      append(outerPath, false)
      moveTo(rect.x + rect.width, rect.y)
      lineTo(rect.x + rect.width, rect.y + rect.height)
      closePath()
    }
    val strokeBackgroundColor = strokeBackgroundKey.takeIf { strokeWidth > 0 }?.let {
      editor.colorsScheme.getColor(it)
    }
    val strokePath = if (strokeBackgroundColor != null) {
      val stroke = toFloatAndScale(strokeWidth)
      // from right top corner to the right bottom corner
      val innerPath = Path2D.Float(Path2D.WIND_EVEN_ODD).apply {
        moveTo(rect.x + rect.width, rect.y + stroke)
        lineTo(rect.x + arc, rect.y + stroke)
        quadTo(rect.x + stroke, rect.y + stroke, rect.x + stroke, rect.y + arc)
        lineTo(rect.x + stroke, rect.y + rect.height - arc)
        quadTo(rect.x + stroke, rect.y + rect.height - stroke,
               rect.x + arc, rect.y + rect.height - stroke)
        lineTo(rect.x + rect.width, rect.y + rect.height - stroke)
      }
      Path2D.Float(Path2D.WIND_EVEN_ODD).apply {
        append(outerPath, false)
        append(innerPath, false)
        moveTo(rect.x + rect.width, rect.y)
        lineTo(rect.x + rect.width, rect.y + stroke)
        moveTo(rect.x + rect.width, rect.y + rect.height - stroke)
        lineTo(rect.x + rect.width, rect.y + rect.height)
        closePath()
      }
    }
    else null

    val g2d = g.create() as Graphics2D
    try {
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2d.color = editor.colorsScheme.getColor(backgroundKey) ?: editor.colorsScheme.getColor(failoverBackgroundKey)
      g2d.fill(path)
      if (strokePath != null) {
        g2d.color = strokeBackgroundColor
        g2d.fill(strokePath)
      }
    }
    finally {
      g2d.dispose()
    }
  }
}
