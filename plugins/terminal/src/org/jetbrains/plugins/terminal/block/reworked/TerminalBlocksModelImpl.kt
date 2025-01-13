// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.max

internal class TerminalBlocksModelImpl(outputModel: TerminalOutputModel) : TerminalBlocksModel {
  private val document: Document = outputModel.editor.document

  private var blockIdCounter: Int = 0

  override val blocks: MutableList<TerminalOutputBlock> = mutableListOf()

  private val mutableEventsFlow: MutableSharedFlow<TerminalBlocksModelEvent> = MutableSharedFlow(replay = Int.MAX_VALUE,
                                                                                                 onBufferOverflow = BufferOverflow.DROP_OLDEST)
  override val events: SharedFlow<TerminalBlocksModelEvent> = mutableEventsFlow.asSharedFlow()

  init {
    document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        if (event.offset == 0 && event.newLength == 0) {
          // The start of the output was trimmed, so we need to remove blocks that became out of bounds.
          // And adjust offsets of the other blocks.
          trimBlocksBefore(offset = event.oldLength)
        }
        else {
          // It can be either an update of the last block or full replace (in case of clear).
          trimBlocksAfter(offset = event.offset)
        }
      }
    })

    val newBlock = createNewBlock(startOffset = 0)
    blocks.add(newBlock)
    mutableEventsFlow.tryEmit(TerminalBlockStartedEvent(newBlock))
  }

  override fun promptStarted(offset: Int) {
    val lastBlock = blocks.last()
    if (offset == lastBlock.startOffset) {
      blocks.removeLast()
      mutableEventsFlow.tryEmit(TerminalBlockRemovedEvent(lastBlock))
    }
    else {
      val updatedBlock = lastBlock.copy(endOffset = offset)
      blocks[blocks.lastIndex] = updatedBlock
      mutableEventsFlow.tryEmit(TerminalBlockFinishedEvent(updatedBlock))
    }

    val newBlock = createNewBlock(offset)
    blocks.add(newBlock)
    mutableEventsFlow.tryEmit(TerminalBlockStartedEvent(newBlock))
  }

  override fun promptFinished(offset: Int) {
    val curBlock = blocks.last()
    blocks[blocks.lastIndex] = curBlock.copy(commandStartOffset = offset)
  }

  override fun commandStarted(offset: Int) {
    val curBlock = blocks.last()
    blocks[blocks.lastIndex] = curBlock.copy(outputStartOffset = offset)
  }

  override fun commandFinished(exitCode: Int) {
    val curBlock = blocks.last()
    blocks[blocks.lastIndex] = curBlock.copy(exitCode = exitCode)
  }

  /**
   * Removes all blocks that end before the [offset] (inclusive), and adjusts all left blocks offsets.
   */
  private fun trimBlocksBefore(offset: Int) {
    val firstNotRemovedBlockIndex = blocks.indexOfFirst { it.endOffset > offset }
    if (firstNotRemovedBlockIndex != -1) {
      repeat(firstNotRemovedBlockIndex) {
        val block = blocks.removeFirst()
        mutableEventsFlow.tryEmit(TerminalBlockRemovedEvent(block))
      }

      for (ind in blocks.indices) {
        val block = blocks[ind]
        blocks[ind] = TerminalOutputBlock(
          id = block.id,
          startOffset = max(block.startOffset - offset, 0),
          commandStartOffset = if (block.commandStartOffset != -1) max(block.commandStartOffset - offset, 0) else -1,
          outputStartOffset = if (block.outputStartOffset != -1) max(block.outputStartOffset - offset, 0) else -1,
          endOffset = max(block.endOffset - offset, 0),
          exitCode = block.exitCode
        )
      }
    }
    else {
      // All text was removed, so remove all blocks and leave an empty initial block.
      for (block in blocks) {
        mutableEventsFlow.tryEmit(TerminalBlockRemovedEvent(block))
      }
      blocks.clear()

      val newBlock = createNewBlock(startOffset = 0)
      blocks.add(newBlock)
      mutableEventsFlow.tryEmit(TerminalBlockStartedEvent(newBlock))
    }
  }

  /**
   * Removes all blocks that start after [offset] and adjusts the end offset of the last block.
   */
  private fun trimBlocksAfter(offset: Int) {
    val firstBlockToRemoveIndex = blocks.indexOfFirst { it.startOffset > offset }
    if (firstBlockToRemoveIndex != -1) {
      repeat(blocks.size - firstBlockToRemoveIndex) {
        val block = blocks.removeLast()
        mutableEventsFlow.tryEmit(TerminalBlockRemovedEvent(block))
      }
    }

    val lastBlock = blocks.last()
    blocks[blocks.lastIndex] = lastBlock.copy(endOffset = document.textLength)
  }

  private fun createNewBlock(startOffset: Int): TerminalOutputBlock {
    return TerminalOutputBlock(
      id = blockIdCounter++,
      startOffset = startOffset,
      commandStartOffset = -1,
      outputStartOffset = -1,
      endOffset = document.textLength,
      exitCode = null
    )
  }

  override fun toString(): String {
    return "TerminalBlocksModelImpl(blocks=$blocks)"
  }
}