// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.block.ui.BlockSeparatorRenderer
import org.jetbrains.plugins.terminal.block.ui.TerminalPromptLeftAreaRenderer
import org.jetbrains.plugins.terminal.block.ui.TerminalPromptSeparatorRenderer
import org.jetbrains.plugins.terminal.block.ui.TerminalUi

internal class TerminalBlocksDecorator(
  private val outputModel: TerminalOutputModel,
  private val blocksModel: TerminalBlocksModel,
  coroutineScope: CoroutineScope,
) {
  private val editor: EditorEx
    get() = outputModel.editor

  /** Block ID to decoration */
  private val decorations: MutableMap<Int, BlockDecoration> = HashMap()

  init {
    coroutineScope.launch(Dispatchers.EDT) {
      blocksModel.events.collect { event ->
        val block = event.block
        when (event) {
          is TerminalBlockStartedEvent -> {
            decorations[block.id] = createPromptDecoration(block)
          }
          is TerminalBlockFinishedEvent -> {
            val decoration = decorations[block.id] ?: error("Decoration not found for block $block")
            disposeDecoration(decoration)
            decorations[block.id] = createFinishedBlockDecoration(block)
          }
          is TerminalBlockRemovedEvent -> {
            val decoration = decorations[block.id] ?: error("Decoration not found for block $block")
            disposeDecoration(decoration)
            decorations.remove(block.id)
          }
        }
      }
    }
  }

  private fun createPromptDecoration(block: TerminalOutputBlock): BlockDecoration {
    val topInlay = createTopInlay(block)
    val bottomInlay = createBottomInlay(block, isFinishedBlock = false)

    val bgHighlighter = createBackgroundHighlighter(block)
    bgHighlighter.isGreedyToRight = true

    val cornersHighlighter = createCornersHighlighter(block).also {
      it.isGreedyToRight = true
      it.customRenderer = TerminalPromptSeparatorRenderer()
      it.lineMarkerRenderer = TerminalPromptLeftAreaRenderer()
    }

    return BlockDecoration(block.id, bgHighlighter, cornersHighlighter, topInlay, bottomInlay)
  }

  private fun createFinishedBlockDecoration(block: TerminalOutputBlock): BlockDecoration {
    val topInlay = createTopInlay(block)
    val bottomInlay = createBottomInlay(block, isFinishedBlock = true)
    val bgHighlighter = createBackgroundHighlighter(block)
    val cornersHighlighter = createCornersHighlighter(block)
    cornersHighlighter.customRenderer = BlockSeparatorRenderer()

    return BlockDecoration(block.id, bgHighlighter, cornersHighlighter, topInlay, bottomInlay)
  }

  private fun disposeDecoration(decoration: BlockDecoration) {
    decoration.backgroundHighlighter.dispose()
    decoration.cornersHighlighter.dispose()
    Disposer.dispose(decoration.topInlay)
    Disposer.dispose(decoration.bottomInlay)
  }

  private fun createTopInlay(block: TerminalOutputBlock): Inlay<*> {
    val topRenderer = EmptyWidthInlayRenderer {
      // Reserve the place for the separator if it is not a first block
      val separatorHeight = if (blocksModel.blocks.firstOrNull()?.id == block.id) 0 else 1
      TerminalUi.blockTopInset + separatorHeight
    }
    return editor.inlayModel.addBlockElement(block.startOffset, false, true, 1, topRenderer)!!
  }

  private fun createBottomInlay(block: TerminalOutputBlock, isFinishedBlock: Boolean): Inlay<*> {
    val bottomRenderer = EmptyWidthInlayRenderer(TerminalUi.blockBottomInset)
    // End offset of the finished block is located after the line break, but we need to place the inlay on the line break offset.
    // So adjust the offset by 1 in this case.
    val offsetDelta = if (isFinishedBlock) 1 else 0
    return editor.inlayModel.addBlockElement(block.endOffset - offsetDelta, true, false, 0, bottomRenderer)!!
  }

  private fun createBackgroundHighlighter(block: TerminalOutputBlock): RangeHighlighter {
    return editor.markupModel.addRangeHighlighter(
      block.startOffset,
      block.endOffset,
      HighlighterLayer.LAST,  // the order doesn't matter because there is only a custom renderer with its own order
      null,
      HighlighterTargetArea.LINES_IN_RANGE
    )
  }

  private fun createCornersHighlighter(block: TerminalOutputBlock): RangeHighlighter {
    return editor.markupModel.addRangeHighlighter(
      block.startOffset,
      block.endOffset,
      HighlighterLayer.FIRST - 100,  // the line marker should be painted first, because it is painting the block background
      null,
      HighlighterTargetArea.LINES_IN_RANGE
    )
  }

  private data class BlockDecoration(
    val blockId: Int,
    val backgroundHighlighter: RangeHighlighter,
    val cornersHighlighter: RangeHighlighter,
    val topInlay: Inlay<*>,
    val bottomInlay: Inlay<*>,
  )

  /** Inlay to just create the space between lines */
  private class EmptyWidthInlayRenderer(private val heightSupplier: () -> Int) : EditorCustomElementRenderer {
    constructor(height: Int) : this({ height })

    override fun calcWidthInPixels(inlay: Inlay<*>): Int = 0

    override fun calcHeightInPixels(inlay: Inlay<*>): Int = JBUI.scale(heightSupplier())
  }
}