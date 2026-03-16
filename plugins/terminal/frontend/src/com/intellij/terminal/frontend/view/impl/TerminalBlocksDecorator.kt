// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.block.BlockTerminalOptions
import org.jetbrains.plugins.terminal.block.BlockTerminalOptionsListener
import org.jetbrains.plugins.terminal.block.ui.BlockSeparatorRenderer
import org.jetbrains.plugins.terminal.block.ui.TerminalBlockLeftErrorRenderer
import org.jetbrains.plugins.terminal.block.ui.TerminalPromptLeftAreaRenderer
import org.jetbrains.plugins.terminal.block.ui.TerminalPromptSeparatorRenderer
import org.jetbrains.plugins.terminal.block.ui.TerminalUi
import org.jetbrains.plugins.terminal.block.ui.VerticalSpaceInlayRenderer
import org.jetbrains.plugins.terminal.block.ui.doTerminalOutputScrollChangingAction
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalBlockAddedEvent
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalBlockId
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalBlockRemovedEvent
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalBlocksModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalBlocksModelEvent
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalBlocksModelListener
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalBlocksReplacedEvent
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock

@ApiStatus.Internal
class TerminalBlocksDecorator(
  private val editor: EditorEx,
  private val outputModel: TerminalOutputModel,
  private val blocksModel: TerminalBlocksModel,
  private val scrollingModel: TerminalOutputScrollingModel,
  coroutineScope: CoroutineScope,
) {
  /** Block ID to decoration */
  @VisibleForTesting
  val decorations: MutableMap<TerminalBlockId, BlockDecoration> = HashMap()

  init {
    blocksModel.addListener(coroutineScope.asDisposable(), object : TerminalBlocksModelListener {
      override fun blockAdded(event: TerminalBlockAddedEvent) {
        handleBlocksModelEvent(event)
      }

      override fun blockRemoved(event: TerminalBlockRemovedEvent) {
        handleBlocksModelEvent(event)
      }

      override fun blocksReplaced(event: TerminalBlocksReplacedEvent) {
        handleBlocksModelEvent(event)
      }
    })

    val options = BlockTerminalOptions.getInstance()
    options.addListener(coroutineScope.asDisposable(), object : BlockTerminalOptionsListener {
      override fun showSeparatorsBetweenBlocksChanged(shouldShow: Boolean) {
        if (shouldShow) {
          createDecorationsForAllBlocks()
        }
        else disposeAllDecorations()
      }
    })

    if (options.showSeparatorsBetweenBlocks) {
      createDecorationsForAllBlocks()
    }
  }

  private fun handleBlocksModelEvent(event: TerminalBlocksModelEvent) {
    if (!BlockTerminalOptions.getInstance().showSeparatorsBetweenBlocks) {
      // Do not add decorations if it is disabled in the settings.
      return
    }

    editor.doTerminalOutputScrollChangingAction {
      doHandleBlocksModelEvent(event)
    }

    scrollingModel.scrollToCursor(force = false)
  }

  private fun doHandleBlocksModelEvent(event: TerminalBlocksModelEvent) {
    when (event) {
      is TerminalBlockAddedEvent -> {
        val previousBlock = blocksModel.blocks.getOrNull(blocksModel.blocks.lastIndex - 1) as? TerminalCommandBlock
        if (previousBlock != null) {
          val decoration = decorations[previousBlock.id] ?: error("Decoration not found for block $previousBlock")
          disposeDecoration(decoration)
          decorations[previousBlock.id] = createFinishedBlockDecoration(previousBlock)
        }

        val block = event.block as? TerminalCommandBlock ?: return
        decorations[block.id] = createPromptDecoration(block)
      }
      is TerminalBlockRemovedEvent -> {
        val block = event.block
        val decoration = decorations[block.id] ?: return
        disposeDecoration(decoration)
        decorations.remove(block.id)
      }
      is TerminalBlocksReplacedEvent -> {
        disposeAllDecorations()
        createDecorationsForAllBlocks()
      }
    }
  }

  private fun createPromptDecoration(block: TerminalCommandBlock): BlockDecoration {
    val startOffset = block.startOffset.coerceAtLeast(outputModel.startOffset)
    val endOffset = block.endOffset

    val topInlay = createTopInlay(block)

    val bgHighlighter = createBackgroundHighlighter(startOffset, endOffset)
    bgHighlighter.isGreedyToRight = true

    val cornersHighlighter = createCornersHighlighter(startOffset, endOffset).also {
      it.isGreedyToLeft = true
      it.isGreedyToRight = true
      it.setCustomRenderer(TerminalPromptSeparatorRenderer())
      it.lineMarkerRenderer = TerminalPromptLeftAreaRenderer()
    }

    return BlockDecoration(block.id, bgHighlighter, cornersHighlighter, topInlay, bottomInlay = null)
  }

  private fun createFinishedBlockDecoration(block: TerminalCommandBlock): BlockDecoration {
    val startOffset = block.startOffset.coerceAtLeast(outputModel.startOffset)
    // End offset of the finished block is located after the line break.
    // But we need to place the end inlay before the line break and limit the height of the highlighters to the block content.
    // So adjust the offset by 1.
    val endOffset = (block.endOffset - 1).coerceAtLeast(outputModel.startOffset)

    val topInlay = createTopInlay(block)
    val bottomInlay = createBottomInlay(endOffset)
    val bgHighlighter = createBackgroundHighlighter(startOffset, endOffset)

    val cornersHighlighter = createCornersHighlighter(startOffset, endOffset)
    cornersHighlighter.setCustomRenderer(BlockSeparatorRenderer())
    if (block.exitCode != null && block.exitCode != 0) {
      cornersHighlighter.lineMarkerRenderer = TerminalBlockLeftErrorRenderer()
    }

    return BlockDecoration(block.id, bgHighlighter, cornersHighlighter, topInlay, bottomInlay)
  }

  private fun createDecorationsForAllBlocks() {
    check(decorations.isEmpty()) { "Decorations map should be empty" }

    val blocks = blocksModel.blocks
    for (ind in blocks.indices) {
      val block = blocks[ind] as? TerminalCommandBlock ?: continue
      decorations[block.id] = if (ind < blocks.lastIndex) {
        createFinishedBlockDecoration(block)
      }
      else {
        createPromptDecoration(block)
      }
    }
  }

  private fun disposeAllDecorations() {
    for (decoration in decorations.values) {
      disposeDecoration(decoration)
    }
    decorations.clear()
  }

  private fun disposeDecoration(decoration: BlockDecoration) {
    decoration.backgroundHighlighter.dispose()
    decoration.cornersHighlighter.dispose()
    Disposer.dispose(decoration.topInlay)
    decoration.bottomInlay?.let { Disposer.dispose(it) }
  }

  private fun createTopInlay(block: TerminalCommandBlock): Inlay<*> {
    val topRenderer = VerticalSpaceInlayRenderer {
      val isFirstBlock = blocksModel.blocks.firstOrNull()?.id == block.id
      if (isFirstBlock) {
        0  // Do not add space if it is the first block
      }
      else {
        TerminalUi.blockTopInset + 1 // Add 1 to reserve the place for the separator
      }
    }
    return editor.inlayModel.addBlockElement(block.startOffset.toRelative(outputModel), false, true, TerminalUi.blockTopInlayPriority, topRenderer)!!
  }

  private fun createBottomInlay(offset: TerminalOffset): Inlay<*> {
    val bottomRenderer = VerticalSpaceInlayRenderer(TerminalUi.blockBottomInset)
    return editor.inlayModel.addBlockElement(offset.toRelative(outputModel), true, false, TerminalUi.blockBottomInlayPriority, bottomRenderer)!!
  }

  private fun createBackgroundHighlighter(startOffset: TerminalOffset, endOffset: TerminalOffset): RangeHighlighter {
    return editor.markupModel.addRangeHighlighter(
      startOffset.toRelative(outputModel),
      endOffset.toRelative(outputModel),
      HighlighterLayer.LAST,  // the order doesn't matter because there is only a custom renderer with its own order
      null,
      HighlighterTargetArea.LINES_IN_RANGE
    )
  }

  private fun createCornersHighlighter(startOffset: TerminalOffset, endOffset: TerminalOffset): RangeHighlighter {
    return editor.markupModel.addRangeHighlighter(
      startOffset.toRelative(outputModel),
      endOffset.toRelative(outputModel),
      HighlighterLayer.FIRST - 100,  // the line marker should be painted first, because it is painting the block background
      null,
      HighlighterTargetArea.LINES_IN_RANGE
    )
  }

  data class BlockDecoration(
    val blockId: TerminalBlockId,
    val backgroundHighlighter: RangeHighlighter,
    val cornersHighlighter: RangeHighlighter,
    val topInlay: Inlay<*>,
    val bottomInlay: Inlay<*>?,
  )
}