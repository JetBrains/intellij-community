// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.terminal.exp.ui.TerminalBlockBackgroundRenderer
import org.jetbrains.plugins.terminal.exp.ui.TerminalBlockCornersRenderer
import org.jetbrains.plugins.terminal.exp.ui.TerminalBlockLeftAreaRenderer
import java.awt.Graphics

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
}

data class BlockDecoration(val backgroundHighlighter: RangeHighlighter,
                           val cornersHighlighter: RangeHighlighter,
                           val topInlay: Inlay<*>,
                           val bottomInlay: Inlay<*>)