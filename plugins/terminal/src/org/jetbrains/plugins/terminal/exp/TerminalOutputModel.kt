// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.RangeMarkerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.Rectangle
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

class TerminalOutputModel(val editor: EditorEx) {
  private val blocks: MutableList<CommandBlock> = Collections.synchronizedList(ArrayList())
  private val decorations: MutableMap<CommandBlock, BlockDecoration> = HashMap()
  private val highlightings: MutableMap<CommandBlock, List<HighlightingInfo>> = LinkedHashMap()  // order matters
  private val blockStates: MutableMap<CommandBlock, List<BlockDecorationState>> = HashMap()

  private val document: Document = editor.document
  private val listeners: MutableList<TerminalOutputListener> = CopyOnWriteArrayList()

  fun addListener(listener: TerminalOutputListener, disposable: Disposable? = null) {
    listeners.add(listener)
    if (disposable != null) {
      Disposer.register(disposable) { listeners.remove(listener) }
    }
  }

  @RequiresEdt
  fun createBlock(command: String?): CommandBlock {
    closeLastBlock()

    if (document.textLength > 0) {
      document.insertString(document.textLength, "\n")
    }
    val marker = document.createRangeMarker(document.textLength, document.textLength)
    marker.isGreedyToRight = true
    val block = CommandBlock(command, marker)
    blocks.add(block)
    return block
  }

  @RequiresEdt
  fun closeLastBlock() {
    val lastBlock = getLastBlock()
    // restrict previous block expansion
    if (lastBlock != null) {
      lastBlock.range.isGreedyToRight = false
      decorations[lastBlock]?.let {
        it.backgroundHighlighter.isGreedyToRight = false
        it.cornersHighlighter.isGreedyToRight = false
        (it.bottomInlay as RangeMarkerImpl).isStickingToRight = false
      }
    }
  }

  @RequiresEdt
  fun removeBlock(block: CommandBlock) {
    document.deleteString(block.startOffset, block.endOffset)

    block.range.dispose()
    decorations[block]?.let {
      Disposer.dispose(it.topInlay)
      Disposer.dispose(it.bottomInlay)
      it.commandToOutputInlay?.let { inlay -> Disposer.dispose(inlay) }
      editor.markupModel.removeHighlighter(it.backgroundHighlighter)
      editor.markupModel.removeHighlighter(it.cornersHighlighter)
    }

    blocks.remove(block)
    decorations.remove(block)
    highlightings.remove(block)
    blockStates.remove(block)
  }

  fun getLastBlock(): CommandBlock? {
    return blocks.lastOrNull()
  }

  fun getByOffset(offset: Int): CommandBlock? {
    // todo: better to use binary search here, but default implementation doesn't not acquire the lock of the list
    return blocks.find { offset in (it.startOffset..it.endOffset) }
  }

  fun getByIndex(index: Int): CommandBlock {
    return blocks[index]
  }

  fun getIndexOfBlock(block: CommandBlock): Int {
    return blocks.indexOf(block)
  }

  @RequiresEdt
  fun getBlockBounds(block: CommandBlock): Rectangle {
    val topY = editor.offsetToXY(block.startOffset).y - TerminalUi.blockTopInset
    val bottomY = editor.offsetToXY(block.endOffset).y + editor.lineHeight + TerminalUi.blockBottomInset
    val width = editor.scrollingModel.visibleArea.width - TerminalUi.cornerToBlockInset
    return Rectangle(0, topY, width, bottomY - topY)
  }

  fun getBlocksSize(): Int = blocks.size

  @RequiresEdt
  fun getDecoration(block: CommandBlock): BlockDecoration? {
    return decorations[block]
  }

  @RequiresEdt
  fun putDecoration(block: CommandBlock, decoration: BlockDecoration) {
    decorations[block] = decoration
  }

  @RequiresEdt
  fun getAllHighlightings(): List<HighlightingInfo> {
    return highlightings.flatMap { it.value }
  }

  @RequiresEdt
  fun getHighlightings(block: CommandBlock): List<HighlightingInfo>? {
    return highlightings[block]
  }

  @RequiresEdt
  fun putHighlightings(block: CommandBlock, highlightings: List<HighlightingInfo>) {
    this.highlightings[block] = highlightings
  }

  @RequiresEdt
  fun getBlockState(block: CommandBlock): List<BlockDecorationState> {
    return blockStates[block] ?: emptyList()
  }

  @RequiresEdt
  fun addBlockState(block: CommandBlock, state: BlockDecorationState) {
    val curStates = blockStates[block] ?: listOf()
    if (curStates.find { it.name == state.name } == null) {
      updateBlockStates(block, curStates, curStates.toMutableList() + state)
    }
  }

  @RequiresEdt
  fun removeBlockState(block: CommandBlock, stateName: String) {
    val curStates = blockStates[block]
    if (curStates?.find { it.name == stateName } != null) {
      updateBlockStates(block, curStates, curStates.filter { it.name != stateName })
    }
  }

  private fun updateBlockStates(block: CommandBlock, oldStates: List<BlockDecorationState>, newStates: List<BlockDecorationState>) {
    blockStates[block] = newStates
    listeners.forEach { it.blockDecorationStateChanged(block, oldStates, newStates) }
  }

  interface TerminalOutputListener {
    fun blockRemoved(block: CommandBlock) {}
    fun blockDecorationStateChanged(block: CommandBlock, oldStates: List<BlockDecorationState>, newStates: List<BlockDecorationState>) {}
  }
}

data class CommandBlock(val command: String?, val range: RangeMarker) {
  val startOffset: Int
    get() = range.startOffset
  val endOffset: Int
    get() = range.endOffset
  val outputStartOffset: Int
    get() = range.startOffset + if (!command.isNullOrEmpty()) command.length + 1 else 0
  val textRange: TextRange
    get() = range.textRange
}