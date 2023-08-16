// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.RangeMarkerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange

class TerminalOutputModel(private val editor: EditorEx) {
  private val blocks: MutableList<CommandBlock> = mutableListOf()
  private val decorations: MutableMap<CommandBlock, BlockDecoration> = HashMap()
  private val highlightings: MutableMap<CommandBlock, List<HighlightingInfo>> = LinkedHashMap()  // order matters

  private val document: Document = editor.document

  fun createBlock(command: String?): CommandBlock {
    val lastBlock = getLastBlock()
    // restrict previous block expansion
    if (lastBlock != null) {
      lastBlock.range.isGreedyToRight = false
      decorations[lastBlock]?.let {
        it.highlighter.isGreedyToRight = false
        (it.bottomInlay as RangeMarkerImpl).isStickingToRight = false
      }
    }

    val textLength = document.textLength
    val startOffset = if (textLength != 0) {
      document.insertString(textLength, "\n")
      textLength + 1
    }
    else 0
    val marker = document.createRangeMarker(startOffset, startOffset)
    marker.isGreedyToRight = true
    val block = CommandBlock(command, marker)
    blocks.add(block)
    return block
  }

  fun removeBlock(block: CommandBlock) {
    document.deleteString(block.startOffset, block.endOffset)

    block.range.dispose()
    decorations[block]?.let {
      Disposer.dispose(it.topInlay)
      Disposer.dispose(it.bottomInlay)
      editor.markupModel.removeHighlighter(it.highlighter)
    }

    blocks.remove(block)
    decorations.remove(block)
    highlightings.remove(block)
  }

  fun getLastBlock(): CommandBlock? {
    return blocks.lastOrNull()
  }

  fun getBlocksSize(): Int = blocks.size

  fun getDecoration(block: CommandBlock): BlockDecoration? {
    return decorations[block]
  }

  fun putDecoration(block: CommandBlock, decoration: BlockDecoration) {
    decorations[block] = decoration
  }

  fun getAllHighlightings(): List<HighlightingInfo> {
    return highlightings.flatMap { it.value }
  }

  fun getHighlightings(block: CommandBlock): List<HighlightingInfo>? {
    return highlightings[block]
  }

  fun putHighlightings(block: CommandBlock, highlightings: List<HighlightingInfo>) {
    this.highlightings[block] = highlightings
  }
}

data class CommandBlock(val command: String?, val range: RangeMarker) {
  val startOffset: Int
    get() = range.startOffset
  val endOffset: Int
    get() = range.endOffset
  val textRange: TextRange
    get() = range.textRange
}