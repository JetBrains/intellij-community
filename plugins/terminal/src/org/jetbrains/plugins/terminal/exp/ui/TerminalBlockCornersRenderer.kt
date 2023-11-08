// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.terminal.exp.TerminalUi
import org.jetbrains.plugins.terminal.exp.TerminalUiUtils.toFloatAndScale
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D

/**
 * Paints the following:
 * 1. Top area of the block with a rounded corner on the right
 * 2. Bottom area of the block with a rounded corner on the right
 * 3. Gap between the blocks
 *
 * It is painted over the selection to override it, so the selection will be painted only in the text area.
 */
class TerminalBlockCornersRenderer private constructor(
  private val background: Color?,
  private val gradientCache: GradientTextureCache?,
  private val strokeBackground: Color? = null,
  private val strokeWidth: Int = 0
) : CustomHighlighterRenderer {
  /** Paints solid background, but also can paint the border if [strokeBackground] is specified and [strokeWidth] is greater than 0 */
  constructor(background: Color, strokeBackground: Color? = null, strokeWidth: Int = 0) : this(
    background = background,
    gradientCache = null,
    strokeBackground = strokeBackground,
    strokeWidth = strokeWidth
  )

  /** Paints the linear gradient from left to right */
  constructor(gradientCache: GradientTextureCache) : this(background = null, gradientCache = gradientCache)


  override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
    val topIns = toFloatAndScale(TerminalUi.blockTopInset)
    val bottomIns = toFloatAndScale(TerminalUi.blockBottomInset)
    // it is used to calculate the width, so it should not contain the fractional part
    // because the width will be used to check that cached gradient is still valid
    val cornerToBlock = JBUI.scale(TerminalUi.cornerToBlockInset).toFloat()
    val gap = toFloatAndScale(TerminalUi.blocksGap)
    val arc = toFloatAndScale(TerminalUi.blockArc)

    val visibleArea = editor.scrollingModel.visibleArea
    val width = visibleArea.width - cornerToBlock
    val topY = editor.offsetToXY(highlighter.startOffset).y - topIns
    val bottomY = editor.offsetToXY(highlighter.endOffset).y + editor.lineHeight + bottomIns

    val topRect = Rectangle2D.Float(0f, topY, width, topIns)
    val bottomRect = Rectangle2D.Float(0f, bottomY - bottomIns, width, bottomIns + gap)
    val topCornerPath = Path2D.Float(Path2D.WIND_EVEN_ODD).apply {
      moveTo(0f, topY)
      lineTo(width - arc, topY)
      quadTo(width, topY, width, topY + arc)
      lineTo(width, topY + topIns)
      lineTo(0f, topY + topIns)
      lineTo(0f, topY)
      closePath()
    }
    val bottomCornerPath = Path2D.Float(Path2D.WIND_EVEN_ODD).apply {
      moveTo(0f, bottomY - bottomIns)
      lineTo(width, bottomY - bottomIns)
      lineTo(width, bottomY - arc)
      quadTo(width, bottomY, width - arc, bottomY)
      lineTo(0f, bottomY)
      lineTo(0f, bottomY - bottomIns)
      closePath()
    }
    val strokePath = if (strokeWidth > 0 && strokeBackground != null) {
      val stroke = toFloatAndScale(strokeWidth)
      val outerPath = Path2D.Float(Path2D.WIND_EVEN_ODD).apply {
        moveTo(0f, topY)
        lineTo(width - arc, topY)
        quadTo(width, topY, width, topY + arc)
        lineTo(width, bottomY - arc)
        quadTo(width, bottomY, width - arc, bottomY)
        lineTo(0f, bottomY)
      }
      val innerPath = Path2D.Float(Path2D.WIND_EVEN_ODD).apply {
        moveTo(0f, topY + stroke)
        lineTo(width - arc, topY + stroke)
        quadTo(width - stroke, topY + stroke, width - stroke, topY + arc)
        lineTo(width - stroke, bottomY - arc)
        quadTo(width - stroke, bottomY - stroke, width - arc, bottomY - stroke)
        lineTo(0f, bottomY - stroke)
      }
      Path2D.Float(Path2D.WIND_EVEN_ODD).apply {
        append(outerPath, false)
        append(innerPath, false)
        moveTo(0f, topY)
        lineTo(0f, topY + stroke)
        moveTo(0f, bottomY - stroke)
        lineTo(0f, bottomY)
        closePath()
      }
    }
    else null

    val g2d = g.create() as Graphics2D
    try {
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2d.color = TerminalUi.terminalBackground
      // override the selection with the default terminal background
      g2d.fill(topRect)
      g2d.fill(bottomRect)

      g2d.paint = gradientCache?.getTexture(g2d, width.toInt()) ?: background
      // paint the top and bottom parts of the block with the rounded corner on the right
      g2d.fill(topCornerPath)
      g2d.fill(bottomCornerPath)

      if (strokePath != null) {
        g2d.paint = strokeBackground
        g2d.fill(strokePath)
      }
    }
    finally {
      g2d.dispose()
    }
  }
}