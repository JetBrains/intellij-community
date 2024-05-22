// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.exp.prompt.TerminalPromptRenderingInfo
import java.awt.Rectangle

/**
 * Designed as a part of MVC pattern.
 * @see TerminalOutputModel
 * @see TerminalOutputView
 * @see TerminalOutputController
 */
@ApiStatus.Internal
interface TerminalOutputModel {
  val editor: EditorEx

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

  /**
   * Active block is the last block if it is able to expand.
   * @return null in three cases:
   * 1. There are no blocks created yet.
   * 2. Requested after user inserted an empty line, but before block for new command is created.
   * 3. Requested after command is started, but before the block is created for it.
   */
  fun getActiveBlock(): CommandBlock?

  fun getLastBlock(): CommandBlock?

  fun getByOffset(offset: Int): CommandBlock?

  fun getByIndex(index: Int): CommandBlock

  fun getIndexOfBlock(block: CommandBlock): Int

  fun getBlocksSize(): Int

  @RequiresEdt
  fun getBlockBounds(block: CommandBlock): Rectangle

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
}
