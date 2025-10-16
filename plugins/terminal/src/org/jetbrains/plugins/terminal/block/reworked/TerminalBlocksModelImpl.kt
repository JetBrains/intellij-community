// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.Disposable
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.session.TerminalBlocksModelState
import org.jetbrains.plugins.terminal.session.TerminalOutputBlock

@ApiStatus.Internal
class TerminalBlocksModelImpl(private val outputModel: TerminalOutputModel, parentDisposable: Disposable) : TerminalBlocksModel {
  @VisibleForTesting
  var blockIdCounter: Int = 0

  override val blocks: MutableList<TerminalOutputBlock> = mutableListOf()

  private val dispatcher = EventDispatcher.create(TerminalBlocksModelListener::class.java)

  init {
    outputModel.addListener(parentDisposable, object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        // first, clean up stuff that's out of bounds now
        trimBlocksBefore(outputModel.startOffset)
        trimBlocksAfter(outputModel.endOffset)
        if (!event.isTrimming) {
          trimBlocksAfter(event.offset)
        }
      }
    })

    addNewBlock(outputModel.startOffset)
  }

  override fun addListener(parentDisposable: Disposable, listener: TerminalBlocksModelListener) {
    dispatcher.addListener(listener, parentDisposable)
  }

  override fun promptStarted(offset: TerminalOffset) {
    val lastBlock = blocks.last()
    if (offset == lastBlock.startOffset) {
      blocks.removeLast()
      dispatcher.multicaster.blockRemoved(TerminalBlockRemovedEventImpl(this, lastBlock))
    }
    else {
      val updatedBlock = lastBlock.copy(endOffset = offset)
      blocks[blocks.lastIndex] = updatedBlock
    }

    addNewBlock(offset)
  }

  override fun promptFinished(offset: TerminalOffset) {
    val curBlock = blocks.last()
    blocks[blocks.lastIndex] = curBlock.copy(commandStartOffset = offset)
  }

  override fun commandStarted(offset: TerminalOffset) {
    val curBlock = blocks.last()
    blocks[blocks.lastIndex] = curBlock.copy(outputStartOffset = offset)
  }

  override fun commandFinished(exitCode: Int) {
    val curBlock = blocks.last()
    blocks[blocks.lastIndex] = curBlock.copy(exitCode = exitCode)
  }

  override fun dumpState(): TerminalBlocksModelState {
    return TerminalBlocksModelState(
      blocks = blocks.toList(),
      blockIdCounter = blockIdCounter
    )
  }

  override fun restoreFromState(state: TerminalBlocksModelState) {
    check(state.blocks.isNotEmpty()) { "There should be always at least one block in the blocks model state" }

    blockIdCounter = state.blockIdCounter
    replaceBlocks(state.blocks)
  }

  /**
   * Removes all blocks that end before the [offset] (inclusive), and adjusts all left blocks offsets.
   */
  private fun trimBlocksBefore(offset: TerminalOffset) {
    val firstNotRemovedBlockIndex = blocks.indexOfFirst { it.endOffset > offset || (it.startOffset == it.endOffset && it.endOffset == offset) }
    if (firstNotRemovedBlockIndex != -1) {
      repeat(firstNotRemovedBlockIndex) {
        val block = blocks.removeFirst()
        dispatcher.multicaster.blockRemoved(TerminalBlockRemovedEventImpl(this, block))
      }
    }
    else {
      // All text was removed, so remove all blocks and leave an empty initial block.
      val newBlock = createNewBlock(outputModel.startOffset)
      replaceBlocks(listOf(newBlock))
    }
  }

  /**
   * Removes all blocks that start after [offset] and adjusts the end offset of the last block.
   */
  private fun trimBlocksAfter(offset: TerminalOffset) {
    val firstBlockToRemoveIndex = blocks.indexOfFirst { it.startOffset > offset }
    if (firstBlockToRemoveIndex != -1) {
      repeat(blocks.size - firstBlockToRemoveIndex) {
        val block = blocks.removeLast()
        dispatcher.multicaster.blockRemoved(TerminalBlockRemovedEventImpl(this, block))
      }
    }

    if (blocks.isEmpty()) {
      addNewBlock(outputModel.startOffset)
    }
    else {
      val lastBlock = blocks.last()
      blocks[blocks.lastIndex] = lastBlock.copy(endOffset = outputModel.endOffset)
    }
  }

  private fun replaceBlocks(newBlocks: List<TerminalOutputBlock>) {
    val oldBlocks = blocks.toList()
    blocks.clear()
    blocks.addAll(newBlocks)
    dispatcher.multicaster.blocksReplaced(TerminalBlocksReplacedEventImpl(this, oldBlocks, newBlocks))
  }

  private fun addNewBlock(startOffset: TerminalOffset) {
    val newBlock = createNewBlock(startOffset)
    blocks.add(newBlock)
    dispatcher.multicaster.blockAdded(TerminalBlockAddedEventImpl(this, newBlock))
  }

  private fun createNewBlock(startOffset: TerminalOffset): TerminalOutputBlock {
    return TerminalOutputBlock(
      id = blockIdCounter++,
      startOffset = startOffset,
      commandStartOffset = null,
      outputStartOffset = null,
      endOffset = outputModel.endOffset,
      exitCode = null
    )
  }

  override fun toString(): String {
    return "TerminalBlocksModelImpl(blocks=$blocks)"
  }
}

private data class TerminalBlockAddedEventImpl(
  override val model: TerminalBlocksModel,
  override val block: TerminalOutputBlock,
) : TerminalBlockAddedEvent

private data class TerminalBlockRemovedEventImpl(
  override val model: TerminalBlocksModel,
  override val block: TerminalOutputBlock,
) : TerminalBlockRemovedEvent

private data class TerminalBlocksReplacedEventImpl(
  override val model: TerminalBlocksModel,
  override val oldBlocks: List<TerminalOutputBlock>,
  override val newBlocks: List<TerminalOutputBlock>,
) : TerminalBlocksReplacedEvent

/**
 * Returns true if the user can type a command right now.
 */
@ApiStatus.Internal
@RequiresEdt
fun TerminalBlocksModel.isCommandTypingMode(): Boolean {
  val lastBlock = blocks.lastOrNull() ?: return false
  // The command start offset is where the prompt ends.
  // If it's not there yet, it means the user can't type a command yet.
  // The output start offset is -1 until the command starts executing.
  // Once that happens, it means the user can't type anymore.
  return lastBlock.commandStartOffset != null && lastBlock.outputStartOffset == null
}