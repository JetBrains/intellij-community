// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptRenderingInfo
import org.jetbrains.plugins.terminal.block.ui.TerminalUi
import java.awt.Rectangle

/**
 * TODO: this interface is quite big.
 *  Consider extracting [CommandBlockInfo] related things and/or highlightings?
 * Designed as a part of MVC pattern.
 * @see TerminalOutputModel
 * @see TerminalOutputView
 * @see TerminalOutputController
 */
@ApiStatus.Internal
interface TerminalOutputModel {
  val editor: EditorEx

  /**
   * List of command output blocks.
   * The first block is the top one, the last is at the bottom.
   * Note that the returned list is not a view and can be changed at any moment.
   */
  val blocks: List<CommandBlock>

  /**
   * @param terminalWidth number of columns at the moment of block creation. It is used to properly position the right prompt.
   */
  @RequiresEdt
  fun createBlock(command: String?, prompt: TerminalPromptRenderingInfo?, terminalWidth: Int): CommandBlock

  @RequiresEdt
  fun finalizeBlock(activeBlock: CommandBlock)

  @RequiresEdt
  fun removeBlock(block: CommandBlock)

  @RequiresEdt
  fun clearBlocks()

  @RequiresEdt
  fun trimOutput()

  @RequiresEdt
  fun getHighlightings(block: CommandBlock): List<HighlightingInfo>

  @RequiresEdt
  fun putHighlightings(block: CommandBlock, highlightings: List<HighlightingInfo>)

  @RequiresEdt
  fun getHighlightingsSnapshot(): TerminalOutputHighlightingsSnapshot

  @RequiresEdt
  fun setBlockInfo(block: CommandBlock, info: CommandBlockInfo)

  @RequiresEdt
  fun getBlockInfo(block: CommandBlock): CommandBlockInfo?

  fun addListener(listener: TerminalOutputModelListener, disposable: Disposable? = null)

  companion object {
    val KEY: DataKey<TerminalOutputModel> = DataKey.create("TerminalOutputModel")
  }
}

/**
 * Active block is the last block if it is able to expand.
 * @return null in three cases:
 * 1. There are no blocks created yet.
 * 2. Requested after user inserted an empty line, but before block for new command is created.
 * 3. Requested after command is started, but before the block is created for it.
 */
internal fun TerminalOutputModel.getActiveBlock(): CommandBlock? {
  return blocks.lastOrNull()?.takeIf { !it.isFinalized }
}

internal fun TerminalOutputModel.getByOffset(offset: Int): CommandBlock? {
  // todo: better to use binary search here, but default implementation doesn't not acquire the lock of the list
  return blocks.find { offset in (it.startOffset..it.endOffset) }
}

@RequiresEdt
internal fun TerminalOutputModel.getBlockBounds(block: CommandBlock): Rectangle {
  val topY = editor.offsetToXY(block.startOffset).y - TerminalUi.blockTopInset
  val bottomY = editor.offsetToXY(block.endOffset).y + editor.lineHeight + TerminalUi.blockBottomInset
  val width = editor.scrollingModel.visibleArea.width - TerminalUi.cornerToBlockOffset
  return Rectangle(0, topY, width, bottomY - topY)
}

internal fun TerminalOutputModel.isErrorBlock(block: CommandBlock): Boolean {
  return getBlockInfo(block).let { it != null && it.exitCode != 0 }
}