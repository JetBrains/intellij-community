// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.session.TerminalOutputBlock
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.block.BlockTerminalOptions
import org.jetbrains.plugins.terminal.block.BlockTerminalOptionsListener
import org.jetbrains.plugins.terminal.block.reworked.*
import org.jetbrains.plugins.terminal.block.ui.*
import kotlin.math.max

internal class TerminalBlocksDecorator(
  private val editor: EditorEx,
  private val blocksModel: TerminalBlocksModel,
  private val scrollingModel: TerminalOutputScrollingModel,
  coroutineScope: CoroutineScope,
) {
  /** Block ID to decoration */
  private val decorations: MutableMap<Int, BlockDecoration> = HashMap()

  init {
    coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      blocksModel.events.collect { event ->
        editor.doTerminalOutputScrollChangingAction {
          handleBlocksModelEvent(event)
        }

        scrollingModel.scrollToCursor(force = false)
      }
    }

    BlockTerminalOptions.Companion.getInstance().addListener(coroutineScope.asDisposable(), object : BlockTerminalOptionsListener {
      override fun showSeparatorsBetweenBlocksChanged(shouldShow: Boolean) {
        if (shouldShow) {
          createDecorationsForAllBlocks()
        }
        else disposeAllDecorations()
      }
    })
  }

  private fun handleBlocksModelEvent(event: TerminalBlocksModelEvent) {
    if (!BlockTerminalOptions.Companion.getInstance().showSeparatorsBetweenBlocks) {
      // Do not add decorations if it is disabled in the settings.
      return
    }

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

    val bgHighlighter = createBackgroundHighlighter(startOffset, endOffset)
    bgHighlighter.isGreedyToRight = true

    val cornersHighlighter = createCornersHighlighter(startOffset, endOffset).also {
      it.isGreedyToRight = true
      it.setCustomRenderer(TerminalPromptSeparatorRenderer())
      it.lineMarkerRenderer = TerminalPromptLeftAreaRenderer()
    }

    return BlockDecoration(block.id, bgHighlighter, cornersHighlighter, topInlay, bottomInlay = null)
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
      val block = blocks[ind]
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

  private fun createTopInlay(block: TerminalOutputBlock): Inlay<*> {
    val topRenderer = VerticalSpaceInlayRenderer {
      val isFirstBlock = blocksModel.blocks.firstOrNull()?.id == block.id
      if (isFirstBlock) {
        0  // Do not add space if it is the first block
      }
      else {
        TerminalUi.blockTopInset + 1 // Add 1 to reserve the place for the separator
      }
    }
    return editor.inlayModel.addBlockElement(block.startOffset, false, true, TerminalUi.blockTopInlayPriority, topRenderer)!!
  }

  private fun createBottomInlay(offset: Int): Inlay<*> {
    val bottomRenderer = VerticalSpaceInlayRenderer(TerminalUi.blockBottomInset)
    return editor.inlayModel.addBlockElement(offset, true, false, TerminalUi.blockBottomInlayPriority, bottomRenderer)!!
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
    val bottomInlay: Inlay<*>?,
  )
}