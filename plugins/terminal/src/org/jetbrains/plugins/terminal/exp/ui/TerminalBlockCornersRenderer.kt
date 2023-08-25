// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.util.ui.GraphicsUtil
import org.jetbrains.plugins.terminal.exp.TerminalUi
import org.jetbrains.plugins.terminal.exp.TerminalUiUtils.toFloatAndScale
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
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
class TerminalBlockCornersRenderer : CustomHighlighterRenderer {
  override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
    val topIns = toFloatAndScale(TerminalUi.blockTopInset)
    val bottomIns = toFloatAndScale(TerminalUi.blockBottomInset)
    val cornerToBlock = toFloatAndScale(TerminalUi.cornerToBlockInset)
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

    val g2d = g.create() as Graphics2D
    try {
      GraphicsUtil.setupAntialiasing(g2d)
      g2d.color = TerminalUi.terminalBackground
      // override the selection with the default terminal background
      g2d.fill(topRect)
      g2d.fill(bottomRect)
      g2d.paint = GradientPaint(0f, topY, TerminalUi.blockBackgroundStart,
                                width, topY, TerminalUi.blockBackgroundEnd)
      // paint the top and bottom parts of the block with the rounded corner on the right
      g2d.fill(topCornerPath)
      g2d.fill(bottomCornerPath)
    }
    finally {
      g2d.dispose()
    }
  }
}