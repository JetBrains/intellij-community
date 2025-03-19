// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils.toFloatAndScale
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
internal class TerminalBlockCornersRenderer private constructor(
  private val backgroundKey: ColorKey?,
  private val gradientCache: GradientTextureCache?,
  private val strokeBackgroundKey: ColorKey? = null,
  private val strokeWidth: Int = 0
) : CustomHighlighterRenderer {
  /** Paints solid background, but also can paint the border if [strokeBackgroundKey] is specified and [strokeWidth] is greater than 0 */
  constructor(backgroundKey: ColorKey, strokeBackgroundKey: ColorKey? = null, strokeWidth: Int = 0) : this(
    backgroundKey = backgroundKey,
    gradientCache = null,
    strokeBackgroundKey = strokeBackgroundKey,
    strokeWidth = strokeWidth
  )

  /** Paints the linear gradient from left to right */
  constructor(gradientCache: GradientTextureCache, strokeBackgroundKey: ColorKey? = null, strokeWidth: Int = 0) : this(
    backgroundKey = null,
    gradientCache = gradientCache,
    strokeBackgroundKey = strokeBackgroundKey,
    strokeWidth = strokeWidth
  )

  private val separatorRenderer = BlockSeparatorRenderer()

  override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
    val topIns = toFloatAndScale(TerminalUi.blockTopInset)
    val bottomIns = toFloatAndScale(TerminalUi.blockBottomInset)
    // it is used to calculate the width, so it should not contain the fractional part
    // because the width will be used to check that cached gradient is still valid
    val cornerToBlock = JBUI.scale(TerminalUi.cornerToBlockInset).toFloat()
    val gap = toFloatAndScale(TerminalUi.blocksGap)
    val arc = toFloatAndScale(TerminalUi.blockArc)
    val selectionGap = JBUI.scale(TerminalUi.blockSelectionSeparatorGap)

    val visibleArea = editor.scrollingModel.visibleArea
    val width = visibleArea.width - cornerToBlock
    val topY = editor.offsetToXY(highlighter.startOffset).y - topIns + selectionGap
    val bottomY = editor.offsetToXY(highlighter.endOffset).y + editor.lineHeight + bottomIns - selectionGap

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
    val strokeBackground = strokeBackgroundKey.takeIf { strokeWidth > 0 }?.let {
      editor.colorsScheme.getColor(it)
    }
    val strokePath = if (strokeBackground != null) {
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
      g2d.color = TerminalUi.defaultBackground(editor)
      // override the selection with the default terminal background
      g2d.fill(topRect)
      g2d.fill(bottomRect)

      getBlockBackgroundPaint(editor, g2d, width.toInt(), gradientCache, backgroundKey)?.let {
        g2d.paint = it
        // paint the top and bottom parts of the block with the rounded corners
        g2d.fill(topCornerPath)
        g2d.fill(bottomCornerPath)
      }

      if (strokePath != null) {
        g2d.paint = strokeBackground
        g2d.fill(strokePath)
      }
    }
    finally {
      g2d.dispose()
    }

    separatorRenderer.paint(editor, highlighter, g)
  }
}
