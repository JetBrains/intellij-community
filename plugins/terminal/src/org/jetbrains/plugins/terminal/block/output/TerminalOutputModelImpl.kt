// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.terminal.BlockTerminalColors
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptRenderingInfo
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.jetbrains.plugins.terminal.block.ui.executeInBulk
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max
import kotlin.math.min

/**
 * Stores all blocks content in a single [document], instead of [Document] per block.
 * Blocks are just windows (i.e., substring) over the [document] (see [com.intellij.openapi.editor.RangeMarker]).
 *
 * This serves several reasons:
 * - Performance efficiency - it is faster to render one editor instead of N.
 * - Cross-block actions like selection, copy, focus, etc.
 *
 * Designed as a part of MVC pattern.
 * @see TerminalOutputModel
 * @see TerminalOutputView
 * @see TerminalOutputController
 */
@ApiStatus.Internal
class TerminalOutputModelImpl(override val editor: EditorEx) : TerminalOutputModel {
  override val blocks: MutableList<CommandBlock> = Collections.synchronizedList(ArrayList())
  private val highlightings: MutableMap<CommandBlock, List<HighlightingInfo>> = LinkedHashMap()  // order matters
  private val blockInfos: MutableMap<CommandBlock, CommandBlockInfo> = HashMap()
  private var highlightingsSnapshot: TerminalOutputHighlightingsSnapshot? = null

  /**
   * Stores whole Session output.
   * Mostly is being only appended.
   */
  private val document: Document = editor.document
  private val listeners: MutableList<TerminalOutputModelListener> = CopyOnWriteArrayList()

  override fun addListener(listener: TerminalOutputModelListener, disposable: Disposable?) {
    listeners.add(listener)
    if (disposable != null) {
      Disposer.register(disposable) { listeners.remove(listener) }
    }
  }

  /**
   * @param terminalWidth number of columns at the moment of block creation. It is used to properly position the right prompt.
   */
  @RequiresEdt
  override fun createBlock(command: String?, prompt: TerminalPromptRenderingInfo?, terminalWidth: Int): CommandBlock {
    // Execute document insertions in bulk to make sure that EditorHighlighter
    // is not requesting the highlightings before we set them.
    val block = document.executeInBulk {
      if (document.textLength > 0) {
        document.insertString(document.textLength, "\n")
      }
      val startOffset = document.textLength

      val blockHighlightings = mutableListOf<HighlightingInfo>()

      fun appendTextWithHighlightings(text: String, highlightings: List<HighlightingInfo>) {
        val adjustedHighlightings = highlightings.rebase(document.textLength)
        document.insertString(document.textLength, text)
        blockHighlightings.addAll(adjustedHighlightings)
      }

      if (prompt != null) {
        appendTextWithHighlightings(prompt.text, prompt.highlightings)
      }
      val commandAttributes = TextAttributesKeyAdapter(editor, BlockTerminalColors.COMMAND)
      val commandAndRightPrompt: TextWithHighlightings = createCommandAndRightPromptText(command, prompt, commandAttributes, terminalWidth)
      if (commandAndRightPrompt.text.isNotEmpty()) {
        appendTextWithHighlightings(commandAndRightPrompt.text, commandAndRightPrompt.highlightings)
      }

      val marker = document.createRangeMarker(startOffset, document.textLength)
      marker.isGreedyToRight = true
      val block = CommandBlockImpl(command, prompt?.text, prompt?.rightText, marker, commandAndRightPrompt.text.length)
      blocks.add(block)
      putHighlightings(block, blockHighlightings)
      block
    }
    listeners.forEach { it.blockCreated(block) }
    return block
  }

  @RequiresEdt
  override fun finalizeBlock(activeBlock: CommandBlock) {
    // restrict block expansion
    (activeBlock as CommandBlockImpl).range.isGreedyToRight = false
    listeners.forEach { it.blockFinalized(activeBlock) }
  }

  @RequiresEdt
  override fun removeBlock(block: CommandBlock) {
    val startBlockInd = blocks.indexOf(block)
    check(startBlockInd >= 0)
    val rangeToDelete = findBlockRangeToDelete(block)
    for (blockInd in startBlockInd + 1 until blocks.size) {
      deleteDocumentRangeInHighlightings(blocks[blockInd], rangeToDelete)
    }

    blocks.remove(block)
    highlightings.remove(block)
    highlightingsSnapshot = null

    listeners.forEach { it.blockRemoved(block) }

    // Remove the text after removing the highlightings because removing text will trigger rehighlight
    // and there should be no highlightings at this moment.
    document.deleteString(rangeToDelete.startOffset, rangeToDelete.endOffset)
    (block as CommandBlockImpl).range.dispose()
  }

  private fun findBlockRangeToDelete(block: CommandBlock): TextRange {
    val blockRange = TextRange(block.startOffset, block.endOffset)
    return if (blockRange.startOffset > 0) {
      check(document.charsSequence[blockRange.startOffset - 1] == '\n')
      // also remove the block separator between this block and the previous one
      TextRange(blockRange.startOffset - 1, blockRange.endOffset)
    }
    else if (blockRange.endOffset < document.textLength) {
      check(document.charsSequence[blockRange.endOffset] == '\n')
      // also remove the block separator between this block and the next one
      TextRange(blockRange.startOffset, blockRange.endOffset + 1)
    }
    else {
      blockRange
    }
  }

  @RequiresEdt
  override fun clearBlocks() {
    val blocksCopy = blocks.reversed()
    for (block in blocksCopy) {
      removeBlock(block)
    }
    editor.document.setText("")
  }

  @RequiresEdt
  override fun getHighlightingsSnapshot(): TerminalOutputHighlightingsSnapshot {
    var snapshot = highlightingsSnapshot
    if (snapshot == null) {
      snapshot = TerminalOutputHighlightingsSnapshot(editor.document, highlightings.flatMap { it.value })
      highlightingsSnapshot = snapshot
    }
    return snapshot
  }

  @RequiresEdt
  override fun getHighlightings(block: CommandBlock): List<HighlightingInfo> {
    return highlightings[block] ?: emptyList()
  }

  @RequiresEdt
  override fun putHighlightings(block: CommandBlock, highlightings: List<HighlightingInfo>) {
    this.highlightings[block] = highlightings
    highlightingsSnapshot = null
  }

  @RequiresEdt
  override fun setBlockInfo(block: CommandBlock, info: CommandBlockInfo) {
    blockInfos[block] = info
    listeners.forEach { it.blockInfoUpdated(block, info) }
  }

  @RequiresEdt
  override fun getBlockInfo(block: CommandBlock): CommandBlockInfo? {
    return blockInfos[block]
  }

  @RequiresEdt
  override fun trimOutput() {
    val maxCapacity = TerminalUiUtils.getDefaultMaxOutputLength()
    val textLength = document.textLength
    if (textLength <= maxCapacity) {
      return
    }
    val topBlockCountToRemove = findTopBlockCountToRemove(maxCapacity, textLength)
    val topBlocksToRemove = blocks.subList(0, topBlockCountToRemove).toList()
    topBlocksToRemove.forEach {
      removeBlock(it)
    }
    trimTopBlock(maxCapacity)
  }

  private fun trimTopBlock(maxCapacity: Int) {
    val textLength = document.textLength
    val textLengthToRemove = textLength - maxCapacity
    if (textLengthToRemove <= 0) {
      return
    }
    val block = blocks.firstOrNull() ?: return
    val outputStartOffset = block.outputStartOffset
    val outputLengthToRemove = min(block.endOffset - outputStartOffset, textLengthToRemove)
    val linesCountBefore = document.lineCount
    deleteDocumentRange(block, TextRange(outputStartOffset, outputStartOffset + outputLengthToRemove))
    block.trimmedLinesCount += linesCountBefore - document.lineCount
  }

  private fun findTopBlockCountToRemove(maxCapacity: Int, textLength: Int): Int {
    val firstRetainedBlockInd = blocks.indexOfFirst {
      it.endOffset + maxCapacity > textLength
    }
    return firstRetainedBlockInd.coerceAtLeast(0)
  }

  @RequiresEdt
  private fun deleteDocumentRange(block: CommandBlock, deleteRange: TextRange) {
    val startBlockInd = blocks.indexOf(block)
    check(startBlockInd >= 0)
    if (!deleteRange.isEmpty) {
      for (blockInd in startBlockInd until blocks.size) {
        deleteDocumentRangeInHighlightings(blocks[blockInd], deleteRange)
      }
      document.deleteString(deleteRange.startOffset, deleteRange.endOffset)
    }
  }

  private fun deleteDocumentRangeInHighlightings(block: CommandBlock, deleteRange: TextRange) {
    val highlightings = getHighlightings(block)
    val updatedHighlightings: List<HighlightingInfo> = highlightings.mapNotNull {
      when {
        it.endOffset <= deleteRange.startOffset -> it
        it.startOffset >= deleteRange.endOffset -> {
          val newRangeStart = it.startOffset - deleteRange.length
          HighlightingInfo(newRangeStart, newRangeStart + it.length, it.textAttributesProvider)
        }
        else -> {
          val intersectionLength = findIntersectionLength(it, deleteRange)
          check(intersectionLength > 0)
          val newRangeStart = min(it.startOffset, deleteRange.startOffset)
          val newRangeEnd = newRangeStart + it.length - intersectionLength
          if (newRangeStart != newRangeEnd)
            HighlightingInfo(newRangeStart, newRangeEnd, it.textAttributesProvider)
          else
            null // the whole highlighting is deleted
        }
      }
    }
    putHighlightings(block, updatedHighlightings)
  }

  private fun findIntersectionLength(range1: HighlightingInfo, range2: TextRange): Int {
    val intersectionLength = min(range1.endOffset, range2.endOffset) - max(range1.startOffset, range2.startOffset)
    return max(intersectionLength, 0)
  }

  companion object {
    @VisibleForTesting
    fun createCommandAndRightPromptText(
      command: String?,
      prompt: TerminalPromptRenderingInfo?,
      commandAttributes: TextAttributesProvider,
      terminalWidth: Int,
    ): TextWithHighlightings {
      val commandText = command ?: ""
      val rightPromptText = prompt?.rightText ?: ""
      if (rightPromptText.isEmpty()) {
        // Most simple case, no right prompt, only command
        return createCommandText(commandText, commandAttributes)
      }
      val promptText = prompt!!.text // if the right prompt text is not empty, prompt is definitely not null
      val promptLastLineLength = promptText.length - (promptText.indexOfLast { it == '\n' } + 1)
      val commandFirstLineLength = commandText.indexOf('\n').takeIf { it != -1 } ?: commandText.length

      return if (promptLastLineLength + commandFirstLineLength + rightPromptText.length < terminalWidth) {
        // The right prompt is fit into the first line of the command. Build the command with the right prompt text.
        val spacesCount = terminalWidth - promptLastLineLength - commandFirstLineLength - rightPromptText.length
        val components = buildList {
          if (commandFirstLineLength > 0) {
            add(TextWithAttributes(commandText.substring(0, commandFirstLineLength), commandAttributes))
          }
          add(TextWithAttributes(" ".repeat(spacesCount), EmptyTextAttributesProvider))
          for (highlighting in prompt.rightHighlightings) {
            add(TextWithAttributes(rightPromptText.substring(highlighting.startOffset, highlighting.endOffset), highlighting.textAttributesProvider))
          }
          if (commandFirstLineLength < commandText.length) {
            add(TextWithAttributes(commandText.substring(commandFirstLineLength), commandAttributes))
          }
        }
        components.toTextWithHighlightings()
      }
      else {
        // The right prompt is not fit into the first line of the command. Return only command.
        createCommandText(commandText, commandAttributes)
      }
    }

    private fun createCommandText(command: String, commandAttributes: TextAttributesProvider): TextWithHighlightings {
      val highlightings = if (command.isNotEmpty()) listOf(HighlightingInfo(0, command.length, commandAttributes)) else emptyList()
      return TextWithHighlightings(command, highlightings)
    }
  }
}

@ApiStatus.Internal
data class CommandBlockInfo(val exitCode: Int)
