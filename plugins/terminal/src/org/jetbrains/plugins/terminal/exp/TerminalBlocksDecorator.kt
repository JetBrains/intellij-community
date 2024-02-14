// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.RangeMarkerImpl
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.BlockTerminalColors
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.terminal.exp.TerminalFocusModel.TerminalFocusListener
import org.jetbrains.plugins.terminal.exp.TerminalOutputModel.TerminalOutputListener
import org.jetbrains.plugins.terminal.exp.TerminalSelectionModel.TerminalSelectionListener
import org.jetbrains.plugins.terminal.exp.ui.GradientTextureCache
import java.awt.Graphics

class TerminalBlocksDecorator(private val outputModel: TerminalOutputModel,
                              private val focusModel: TerminalFocusModel,
                              private val selectionModel: TerminalSelectionModel,
                              private val editor: EditorEx) : TerminalOutputListener {
  private val decorations: MutableMap<CommandBlock, BlockDecoration> = HashMap()

  private val gradientCache: GradientTextureCache = GradientTextureCache(
    scheme = editor.colorsScheme,
    colorStartKey = BlockTerminalColors.BLOCK_BACKGROUND_START,
    colorEndKey = BlockTerminalColors.BLOCK_BACKGROUND_END,
    defaultColorStart = TerminalUi.blockBackgroundStart,
    defaultColorEnd = TerminalUi.blockBackgroundEnd
  )

  init {
    outputModel.addListener(this)
    EditorUtil.disposeWithEditor(editor, gradientCache)
    editor.markupModel.addRangeHighlighter(0, 0,
                                           // the order doesn't matter because there is only custom renderer with its own order
                                           HighlighterLayer.LAST, null,
                                           HighlighterTargetArea.LINES_IN_RANGE).apply {
      isGreedyToLeft = true
      isGreedyToRight = true
      customRenderer = TerminalRightAreaRenderer()
    }

    outputModel.addListener(object : TerminalOutputListener {
      override fun blockFinalized(block: CommandBlock) {
        decorations[block]?.let {
          it.backgroundHighlighter.isGreedyToRight = false
          it.cornersHighlighter.isGreedyToRight = false
          (it.bottomInlay as RangeMarkerImpl).isStickingToRight = false
        }
      }

      override fun blockRemoved(block: CommandBlock) {
        decorations[block]?.let {
          Disposer.dispose(it.topInlay)
          Disposer.dispose(it.bottomInlay)
          it.commandToOutputInlay?.let { inlay -> Disposer.dispose(inlay) }
          editor.markupModel.removeHighlighter(it.backgroundHighlighter)
          editor.markupModel.removeHighlighter(it.cornersHighlighter)
        }
        decorations.remove(block)
      }

      // Highlight the blocks with non-zero exit code as an error
      override fun blockInfoUpdated(block: CommandBlock, newInfo: CommandBlockInfo) {
        updateDecorationState(block)
      }
    })

    // Highlight the selected blocks
    selectionModel.addListener(object : TerminalSelectionListener {
      override fun selectionChanged(oldSelection: List<CommandBlock>, newSelection: List<CommandBlock>) {
        for (block in oldSelection) {
          updateDecorationState(block)
        }
        updateSelectionDecorationState(newSelection)
      }
    })

    // Mark selected blocks as inactive when the terminal loses the focus.
    // Remove inactive state when the terminal receives focus.
    focusModel.addListener(object : TerminalFocusListener {
      override fun activeStateChanged(isActive: Boolean) {
        // Remove inactive state with a delay to make it after selected blocks change.
        // Because otherwise, the old selected block will first become active, and then the selection will be removed.
        // So, it will cause blinking. But with delay, the selection will be removed first, and it won't become active.
        Alarm().addRequest(Runnable {
          if (!editor.isDisposed) {
            updateSelectionDecorationState(selectionModel.selectedBlocks)
          }
        }, 150)
      }
    })
  }

  @RequiresEdt
  fun installDecoration(block: CommandBlock, isFirstBlock: Boolean = false) {
    if (decorations[block] != null) {
      return
    }

    // add additional empty space on top of the block, if it is the first block
    val topInset = TerminalUi.blockTopInset + if (isFirstBlock) TerminalUi.blocksGap else 0
    val topRenderer = EmptyWidthInlayRenderer(topInset)
    val topInlay = editor.inlayModel.addBlockElement(block.startOffset, false, true, 1, topRenderer)!!
    val bottomRenderer = EmptyWidthInlayRenderer(TerminalUi.blockBottomInset + TerminalUi.blocksGap)
    val bottomInlay = editor.inlayModel.addBlockElement(block.endOffset, true, false, 0, bottomRenderer)!!
    val commandToOutputInlay = if (block.withCommand) {
      val renderer = EmptyWidthInlayRenderer(TerminalUi.commandToOutputInset)
      editor.inlayModel.addBlockElement(block.outputStartOffset, false, true, 0, renderer)!!
    }
    else null

    val bgHighlighter = editor.markupModel.addRangeHighlighter(block.startOffset, block.endOffset,
                                                               // the order doesn't matter because there is only custom renderer with its own order
                                                               HighlighterLayer.LAST, null,
                                                               HighlighterTargetArea.LINES_IN_RANGE)
    bgHighlighter.isGreedyToRight = true
    val cornersHighlighter = editor.markupModel.addRangeHighlighter(block.startOffset, block.endOffset,
                                                                    // the line marker should be painted first, because it is painting the block background
                                                                    HighlighterLayer.FIRST - 100, null,
                                                                    HighlighterTargetArea.LINES_IN_RANGE)
    cornersHighlighter.isGreedyToRight = true

    val decoration = BlockDecoration(bgHighlighter, cornersHighlighter, topInlay, bottomInlay, commandToOutputInlay)
    decorations[block] = decoration
    setDecorationState(block, DefaultBlockDecorationState(gradientCache))
  }

  private fun updateDecorationState(block: CommandBlock) {
    val state = calculateDecorationState(block)
    setDecorationState(block, state)
  }

  private fun updateSelectionDecorationState(selectedBlocks: List<CommandBlock>) {
    val state = calculateSelectionDecorationState()
    for (block in selectedBlocks) {
      setDecorationState(block, state)
    }
  }

  private fun calculateDecorationState(block: CommandBlock): BlockDecorationState {
    return if (selectionModel.selectedBlocks.contains(block)) {
      calculateSelectionDecorationState()
    }
    else if (outputModel.getBlockInfo(block).let { it != null && it.exitCode != 0 }) {
      ErrorBlockDecorationState()
    }
    else DefaultBlockDecorationState(gradientCache)
  }

  private fun calculateSelectionDecorationState(): BlockDecorationState {
    return if (focusModel.isActive) SelectedBlockDecorationState() else InactiveSelectedBlockDecorationState()
  }

  private fun setDecorationState(block: CommandBlock, state: BlockDecorationState) {
    val decoration = decorations[block] ?: error("No decoration for block, installDecoration should be called first")
    with(decoration) {
      backgroundHighlighter.customRenderer = state.backgroundRenderer
      cornersHighlighter.customRenderer = state.cornersRenderer
      cornersHighlighter.lineMarkerRenderer = state.leftAreaRenderer
    }

    val bounds = outputModel.getBlockBounds(block)
    editor.component.repaint(bounds)
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

private data class BlockDecoration(val backgroundHighlighter: RangeHighlighter,
                                   val cornersHighlighter: RangeHighlighter,
                                   val topInlay: Inlay<*>,
                                   val bottomInlay: Inlay<*>,
                                   val commandToOutputInlay: Inlay<*>?)