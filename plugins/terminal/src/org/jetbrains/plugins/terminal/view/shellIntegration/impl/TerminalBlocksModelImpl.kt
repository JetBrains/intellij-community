// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.containers.DisposableWrapperList
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.session.impl.TerminalBlocksModelState
import org.jetbrains.plugins.terminal.util.fireListenersAndLogAllExceptions
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.view.shellIntegration.*

@ApiStatus.Internal
class TerminalBlocksModelImpl(
  private val outputModel: TerminalOutputModel,
  private val sessionModel: TerminalSessionModel,
  parentDisposable: Disposable,
) : TerminalBlocksModel {
  @VisibleForTesting
  var blockIdCounter: Int = 0

  override val blocks: MutableList<TerminalBlockBase> = mutableListOf()

  override var activeBlock: TerminalBlockBase
    get() = blocks.last()
    set(value) {
      blocks[blocks.lastIndex] = value
    }

  private val listeners = DisposableWrapperList<TerminalBlocksModelListener>()

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
    listeners.add(listener, parentDisposable)
  }

  fun startNewBlock(offset: TerminalOffset) {
    val active = activeBlock as TerminalCommandBlockImpl
    if (offset == active.startOffset) {
      blocks.removeLast()
      fireListeners(TerminalBlockRemovedEventImpl(this, active))
    }
    else {
      activeBlock = active.copy(endOffset = offset)
    }

    addNewBlock(offset)
  }

  inline fun updateActiveCommandBlock(doUpdate: (TerminalCommandBlockImpl) -> TerminalCommandBlock) {
    activeBlock = doUpdate(activeBlock as TerminalCommandBlockImpl)
  }

  fun dumpState(): TerminalBlocksModelState {
    return TerminalBlocksModelState(
      blocks = blocks.toList(),
      blockIdCounter = blockIdCounter
    )
  }

  fun restoreFromState(state: TerminalBlocksModelState) {
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
        fireListeners(TerminalBlockRemovedEventImpl(this, block))
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
        fireListeners(TerminalBlockRemovedEventImpl(this, block))
      }
    }

    if (blocks.isEmpty()) {
      addNewBlock(outputModel.startOffset)
    }
    else {
      val active = activeBlock as TerminalCommandBlockImpl
      activeBlock = active.copy(endOffset = outputModel.endOffset)
    }
  }

  private fun replaceBlocks(newBlocks: List<TerminalBlockBase>) {
    val oldBlocks = blocks.toList()
    blocks.clear()
    blocks.addAll(newBlocks)
    fireListeners(TerminalBlocksReplacedEventImpl(this, oldBlocks, newBlocks))
  }

  private fun addNewBlock(startOffset: TerminalOffset) {
    val newBlock = createNewBlock(startOffset)
    blocks.add(newBlock)
    fireListeners(TerminalBlockAddedEventImpl(this, newBlock))
  }

  private fun createNewBlock(startOffset: TerminalOffset): TerminalCommandBlock {
    return TerminalCommandBlockImpl(
      id = TerminalBlockIdImpl(blockIdCounter++),
      startOffset = startOffset,
      endOffset = outputModel.endOffset,
      commandStartOffset = null,
      outputStartOffset = null,
      workingDirectory = sessionModel.terminalState.value.currentDirectory.nullize(), // it can be empty string, so use nullize()
      executedCommand = null,
      exitCode = null,
    )
  }

  private fun fireListeners(event: TerminalBlocksModelEvent) {
    fireListenersAndLogAllExceptions(listeners, LOG, "Exception during handling $event") {
      when (event) {
        is TerminalBlockAddedEvent -> it.blockAdded(event)
        is TerminalBlockRemovedEvent -> it.blockRemoved(event)
        is TerminalBlocksReplacedEvent -> it.blocksReplaced(event)
        else -> error("Unexpected event: $event")
      }
    }
  }

  override fun toString(): String {
    return "TerminalBlocksModelImpl(blocks=$blocks)"
  }

  companion object {
    private val LOG = logger<TerminalBlocksModelImpl>()
  }
}

private data class TerminalBlockAddedEventImpl(
  override val model: TerminalBlocksModel,
  override val block: TerminalBlockBase,
) : TerminalBlockAddedEvent

private data class TerminalBlockRemovedEventImpl(
  override val model: TerminalBlocksModel,
  override val block: TerminalBlockBase,
) : TerminalBlockRemovedEvent

private data class TerminalBlocksReplacedEventImpl(
  override val model: TerminalBlocksModel,
  override val oldBlocks: List<TerminalBlockBase>,
  override val newBlocks: List<TerminalBlockBase>,
) : TerminalBlocksReplacedEvent