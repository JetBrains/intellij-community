// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.*
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D

class TerminalBlocksDecorator(private val editor: EditorEx) {
  init {
    editor.markupModel.addRangeHighlighter(0, 0, HighlighterLayer.LAST,
                                           null, HighlighterTargetArea.LINES_IN_RANGE).apply {
      isGreedyToLeft = true
      isGreedyToRight = true
      customRenderer = TerminalRightAreaRenderer()
    }
  }

  fun installDecoration(block: CommandBlock, isFirstBlock: Boolean = false): BlockDecoration {
    // add additional empty space on top of the block, if it is the first block
    val topInset = TerminalUI.blockTopInset + if (isFirstBlock) TerminalUI.blocksGap else 0
    val topRenderer = EmptyWidthInlayRenderer(topInset)
    val topInlay = editor.inlayModel.addBlockElement(block.startOffset, false, true, 1, topRenderer)!!
    val bottomRenderer = EmptyWidthInlayRenderer(TerminalUI.blockBottomInset + TerminalUI.blocksGap)
    val bottomInlay = editor.inlayModel.addBlockElement(block.endOffset, true, false, 0, bottomRenderer)!!

    val attributes = TextAttributes(TerminalUI.outputForeground, TerminalUI.blockBackground, null, null, Font.PLAIN)
    val highlighter = editor.markupModel.addRangeHighlighter(block.startOffset, block.endOffset, HighlighterLayer.FIRST - 100,
                                                             attributes, HighlighterTargetArea.LINES_IN_RANGE)
    highlighter.isGreedyToRight = true
    highlighter.lineMarkerRenderer = TerminalBlockLeftAreaRenderer()
    highlighter.customRenderer = TerminalBlockRenderer()
    return BlockDecoration(highlighter, topInlay, bottomInlay)
  }

  /** Inlay to just create the space between lines */
  private class EmptyWidthInlayRenderer(val height: Int) : EditorCustomElementRenderer {
    override fun calcWidthInPixels(inlay: Inlay<*>): Int = 0

    override fun calcHeightInPixels(inlay: Inlay<*>): Int = JBUI.scale(height)
  }

  /**
   * By default, the background is painted for the whole width of the editor.
   * This renderer overrides the background between blocks' right corner and terminal right corner,
   * so there will be visual separation between the block and the terminal right corner.
   */
  private class TerminalRightAreaRenderer : CustomHighlighterRenderer {
    override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
      val visibleArea = editor.scrollingModel.visibleArea
      val width = JBUI.scale(TerminalUI.cornerToBlockInset)
      val oldColor = g.color
      try {
        g.color = TerminalUI.terminalBackground
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
      val topIns = toFloatAndScale(TerminalUI.blockTopInset)
      val bottomIns = toFloatAndScale(TerminalUI.blockBottomInset)
      val width = toFloatAndScale(TerminalUI.blockLeftInset)
      val arc = toFloatAndScale(TerminalUI.blockArc)

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
        g2d.color = TerminalUI.blockBackground
        g2d.fill(path)
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
   * 3. Gap between the blocks (needed to not paint selection color here)
   */
  private class TerminalBlockRenderer : CustomHighlighterRenderer {
    override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
      val topIns = toFloatAndScale(TerminalUI.blockTopInset)
      val bottomIns = toFloatAndScale(TerminalUI.blockBottomInset)
      val cornerToBlock = toFloatAndScale(TerminalUI.cornerToBlockInset)
      val gap = toFloatAndScale(TerminalUI.blocksGap)
      val arc = toFloatAndScale(TerminalUI.blockArc)

      val visibleArea = editor.scrollingModel.visibleArea
      val width = visibleArea.width - cornerToBlock
      val topY = editor.offsetToXY(highlighter.startOffset).y - topIns
      val bottomY = editor.offsetToXY(highlighter.endOffset).y + editor.lineHeight + bottomIns

      val topRect = Rectangle2D.Float(0f, topY, width, topIns)
      val bottomRect = Rectangle2D.Float(0f, bottomY - bottomIns, width, bottomIns)
      val gapRect = Rectangle2D.Float(0f, bottomY, width, gap)
      val topCornerPath = Path2D.Float(Path2D.WIND_EVEN_ODD).apply {
        moveTo(width - arc, topY)
        lineTo(width, topY)
        lineTo(width, topY + arc)
        quadTo(width, topY, width - arc, topY)
        closePath()
      }
      val bottomCornerPath = Path2D.Float(Path2D.WIND_EVEN_ODD).apply {
        moveTo(width, bottomY - arc)
        lineTo(width, bottomY)
        lineTo(width - arc, bottomY)
        quadTo(width, bottomY, width, bottomY - arc)
        closePath()
      }

      val g2d = g.create() as Graphics2D
      try {
        GraphicsUtil.setupAntialiasing(g2d)
        g2d.color = TerminalUI.blockBackground
        g2d.fill(topRect)
        g2d.fill(bottomRect)
        g2d.color = TerminalUI.terminalBackground
        g2d.fill(gapRect)
        // override the right corners to make them rounded
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

data class BlockDecoration(val highlighter: RangeHighlighter,
                           val topInlay: Inlay<*>,
                           val bottomInlay: Inlay<*>)