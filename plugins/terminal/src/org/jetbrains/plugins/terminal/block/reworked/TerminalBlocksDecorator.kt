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
import org.jetbrains.plugins.terminal.block.ui.*
import kotlin.math.max

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
        editor.doTerminalOutputScrollChangingAction {
          handleBlocksModelEvent(event)
        }
      }
    }
  }

  private fun handleBlocksModelEvent(event: TerminalBlocksModelEvent) {
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

  private fun createPromptDecoration(block: TerminalOutputBlock): BlockDecoration {
    val startOffset = block.startOffset
    val endOffset = block.endOffset

    val topInlay = createTopInlay(block)
    val bottomInlay = createBottomInlay(endOffset)

    val bgHighlighter = createBackgroundHighlighter(startOffset, endOffset)
    bgHighlighter.isGreedyToRight = true

    val cornersHighlighter = createCornersHighlighter(startOffset, endOffset).also {
      it.isGreedyToRight = true
      it.customRenderer = TerminalPromptSeparatorRenderer()
      it.lineMarkerRenderer = TerminalPromptLeftAreaRenderer()
    }

    return BlockDecoration(block.id, bgHighlighter, cornersHighlighter, topInlay, bottomInlay)
  }

  private fun createFinishedBlockDecoration(block: TerminalOutputBlock): BlockDecoration {
    val startOffset = block.startOffset
    // End offset of the finished block is located after the line break.
    // But we need to place the end inlay before the line break and limit the height of the highlighters to the block content.
    // So adjust the offset by 1.
    val endOffset = max(0, block.endOffset - 1)

    val topInlay = createTopInlay(block)
    val bottomInlay = createBottomInlay(endOffset)
    val bgHighlighter = createBackgroundHighlighter(startOffset, endOffset)

    val cornersHighlighter = createCornersHighlighter(startOffset, endOffset)
    cornersHighlighter.customRenderer = BlockSeparatorRenderer()
    if (block.exitCode != null && block.exitCode != 0) {
      cornersHighlighter.lineMarkerRenderer = TerminalBlockLeftErrorRenderer()
    }

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

  private fun createBottomInlay(offset: Int): Inlay<*> {
    val bottomRenderer = EmptyWidthInlayRenderer(TerminalUi.blockBottomInset)
    return editor.inlayModel.addBlockElement(offset, true, false, 0, bottomRenderer)!!
  }

  private fun createBackgroundHighlighter(startOffset: Int, endOffset: Int): RangeHighlighter {
    return editor.markupModel.addRangeHighlighter(
      startOffset,
      endOffset,
      HighlighterLayer.LAST,  // the order doesn't matter because there is only a custom renderer with its own order
      null,
      HighlighterTargetArea.LINES_IN_RANGE
    )
  }

  private fun createCornersHighlighter(startOffset: Int, endOffset: Int): RangeHighlighter {
    return editor.markupModel.addRangeHighlighter(
      startOffset,
      endOffset,
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