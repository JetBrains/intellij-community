// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.*
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D

class TerminalBlocksDecorator(private val editor: EditorEx) {
  init {
    editor.markupModel.addRangeHighlighter(0, 0,
                                           // the order doesn't matter because there is only custom renderer with its own order
                                           HighlighterLayer.LAST, null,
                                           HighlighterTargetArea.LINES_IN_RANGE).apply {
      isGreedyToLeft = true
      isGreedyToRight = true
      customRenderer = TerminalRightAreaRenderer()
    }
  }

  @RequiresEdt
  fun installDecoration(block: CommandBlock, isFirstBlock: Boolean = false): BlockDecoration {
    // add additional empty space on top of the block, if it is the first block
    val topInset = TerminalUi.blockTopInset + if (isFirstBlock) TerminalUi.blocksGap else 0
    val topRenderer = EmptyWidthInlayRenderer(topInset)
    val topInlay = editor.inlayModel.addBlockElement(block.startOffset, false, true, 1, topRenderer)!!
    val bottomRenderer = EmptyWidthInlayRenderer(TerminalUi.blockBottomInset + TerminalUi.blocksGap)
    val bottomInlay = editor.inlayModel.addBlockElement(block.endOffset, true, false, 0, bottomRenderer)!!

    val bgHighlighter = editor.markupModel.addRangeHighlighter(block.startOffset, block.endOffset,
                                                               // the order doesn't matter because there is only custom renderer with its own order
                                                               HighlighterLayer.LAST, null,
                                                               HighlighterTargetArea.LINES_IN_RANGE).apply {
      isGreedyToRight = true
      customRenderer = TerminalBlockBackgroundRenderer()
    }
    val cornersHighlighter = editor.markupModel.addRangeHighlighter(block.startOffset, block.endOffset,
                                                                    // the line marker should be painted first, because it is painting the block background
                                                                    HighlighterLayer.FIRST - 100, null,
                                                                    HighlighterTargetArea.LINES_IN_RANGE).apply {
      isGreedyToRight = true
      lineMarkerRenderer = TerminalBlockLeftAreaRenderer()
      customRenderer = TerminalBlockCornersRenderer()
    }
    return BlockDecoration(bgHighlighter, cornersHighlighter, topInlay, bottomInlay)
  }

  /** Inlay to just create the space between lines */
  private class EmptyWidthInlayRenderer(val height: Int) : EditorCustomElementRenderer {
    override fun calcWidthInPixels(inlay: Inlay<*>): Int = 0

    override fun calcHeightInPixels(inlay: Inlay<*>): Int = JBUI.scale(height)
  }

  /**
   * By default, the selection is painted for the whole width of the editor.
   * This renderer overrides the background between blocks' right corner and terminal right corner,
   * so there will be visual separation between the block and the terminal right corner when there is the selection.
   */
  private class TerminalRightAreaRenderer : CustomHighlighterRenderer {
    override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
      val visibleArea = editor.scrollingModel.visibleArea
      val width = JBUI.scale(TerminalUi.cornerToBlockInset)
      val oldColor = g.color
      try {
        g.color = TerminalUi.terminalBackground
        g.fillRect(visibleArea.width - width, visibleArea.y, width, visibleArea.height)
      }
      finally {
        g.color = oldColor
      }
    }
  }

  /** Paints the left part of the rounded block frame in the gutter area. */
  private class TerminalBlockLeftAreaRenderer : LineMarkerRenderer {
    override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
      val topIns = toFloatAndScale(TerminalUi.blockTopInset)
      val bottomIns = toFloatAndScale(TerminalUi.blockBottomInset)
      val width = toFloatAndScale(TerminalUi.blockLeftInset)
      val arc = toFloatAndScale(TerminalUi.blockArc)

      val gutterWidth = (editor as EditorEx).gutterComponentEx.width
      val rect = Rectangle2D.Float(gutterWidth - width, r.y - topIns, width, r.height + topIns + bottomIns)

      val path = Path2D.Float(Path2D.WIND_EVEN_ODD).apply {
        moveTo(rect.x, rect.y + topIns)
        quadTo(rect.x, rect.y, rect.x + arc, rect.y)
        lineTo(rect.x + rect.width, rect.y)
        lineTo(rect.x + rect.width, rect.y + rect.height)
        lineTo(rect.x + arc, rect.y + rect.height)
        quadTo(rect.x, rect.y + rect.height, rect.x, rect.y + rect.height - arc)
        lineTo(rect.x, rect.y + topIns)
        closePath()
      }
      val g2d = g.create() as Graphics2D
      try {
        GraphicsUtil.setupAntialiasing(g2d)
        g2d.color = TerminalUi.blockBackgroundStart
        g2d.fill(path)
      }
      finally {
        g2d.dispose()
      }
    }
  }

  /**
   * Paints the gradient background of the block, but only in the area of the text (without top, left and bottom corners).
   * So the selection can be painted on top of it.
   */
  private class TerminalBlockBackgroundRenderer : CustomHighlighterRenderer {
    override fun getOrder(): CustomHighlighterOrder = CustomHighlighterOrder.BEFORE_BACKGROUND

    override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
      val visibleArea = editor.scrollingModel.visibleArea
      val width = visibleArea.width - toFloatAndScale(TerminalUi.cornerToBlockInset)
      val topY = editor.offsetToXY(highlighter.startOffset).y.toFloat()
      val bottomY = editor.offsetToXY(highlighter.endOffset).y.toFloat() + editor.lineHeight
      val rect = Rectangle2D.Float(0f, topY, width, bottomY - topY)

      val g2d = g.create() as Graphics2D
      try {
        GraphicsUtil.setupAntialiasing(g2d)
        g2d.paint = GradientPaint(0f, topY, TerminalUi.blockBackgroundStart,
                                  width, topY, TerminalUi.blockBackgroundEnd)
        g2d.fill(rect)
      }
      finally {
        g2d.dispose()
      }
    }
  }

  /**
   * Paints the following:
   * 1. Top area of the block with a rounded corner on the right
   * 2. Bottom area of the block with a rounded corner on the right
   * 3. Gap between the blocks
   *
   * It is painted over the selection to override it, so the selection will be painted only in the text area.
   */
  private class TerminalBlockCornersRenderer : CustomHighlighterRenderer {
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

  companion object {
    private fun toFloatAndScale(value: Int): Float = JBUIScale.scale(value.toFloat())
  }
}

data class BlockDecoration(val backgroundHighlighter: RangeHighlighter,
                           val cornersHighlighter: RangeHighlighter,
                           val topInlay: Inlay<*>,
                           val bottomInlay: Inlay<*>)