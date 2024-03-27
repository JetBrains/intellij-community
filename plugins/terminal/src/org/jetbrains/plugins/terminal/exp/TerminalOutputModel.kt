// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.terminal.BlockTerminalColors
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.exp.prompt.PromptRenderingInfo
import java.awt.Rectangle
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max
import kotlin.math.min

class TerminalOutputModel(val editor: EditorEx) {
  private val blocks: MutableList<CommandBlock> = Collections.synchronizedList(ArrayList())
  private val highlightings: MutableMap<CommandBlock, List<HighlightingInfo>> = LinkedHashMap()  // order matters
  private val blockInfos: MutableMap<CommandBlock, CommandBlockInfo> = HashMap()
  private var allHighlightingsSnapshot: AllHighlightingsSnapshot? = null

  private val document: Document = editor.document
  private val listeners: MutableList<TerminalOutputListener> = CopyOnWriteArrayList()

  fun addListener(listener: TerminalOutputListener, disposable: Disposable? = null) {
    listeners.add(listener)
    if (disposable != null) {
      Disposer.register(disposable) { listeners.remove(listener) }
    }
  }

  /**
   * @param terminalWidth number of columns at the moment of block creation. It is used to properly position the right prompt.
   */
  @RequiresEdt
  fun createBlock(command: String?, prompt: PromptRenderingInfo?, terminalWidth: Int): CommandBlock {
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
  internal fun finalizeBlock(activeBlock: CommandBlock) {
    // restrict block expansion
    (activeBlock as CommandBlockImpl).range.isGreedyToRight = false
    listeners.forEach { it.blockFinalized(activeBlock) }
  }

  @RequiresEdt
  fun removeBlock(block: CommandBlock) {
    val startBlockInd = blocks.indexOf(block)
    check(startBlockInd >= 0)
    val rangeToDelete = findBlockRangeToDelete(block)
    for (blockInd in startBlockInd + 1 until blocks.size) {
      deleteDocumentRangeInHighlightings(blocks[blockInd], rangeToDelete)
    }

    blocks.remove(block)
    highlightings.remove(block)
    allHighlightingsSnapshot = null

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
  fun clearBlocks() {
    val blocksCopy = blocks.reversed()
    for (block in blocksCopy) {
      removeBlock(block)
    }
    editor.document.setText("")
  }

  /**
   * Active block is the last block if it is able to expand.
   * @return null in three cases:
   * 1. There are no blocks created yet.
   * 2. Requested after user inserted an empty line, but before block for new command is created.
   * 3. Requested after command is started, but before the block is created for it.
   */
  fun getActiveBlock(): CommandBlock? {
    return blocks.lastOrNull()?.takeIf { !it.isFinalized }
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
  internal fun getHighlightingsSnapshot(): AllHighlightingsSnapshot {
    var snapshot: AllHighlightingsSnapshot? = allHighlightingsSnapshot
    if (snapshot == null) {
      snapshot = AllHighlightingsSnapshot(editor.document, highlightings.flatMap { it.value })
      allHighlightingsSnapshot = snapshot
    }
    return snapshot
  }

  @RequiresEdt
  fun getHighlightings(block: CommandBlock): List<HighlightingInfo> {
    return highlightings[block] ?: emptyList()
  }

  @RequiresEdt
  fun putHighlightings(block: CommandBlock, highlightings: List<HighlightingInfo>) {
    this.highlightings[block] = highlightings
    allHighlightingsSnapshot = null
  }

  @RequiresEdt
  fun setBlockInfo(block: CommandBlock, info: CommandBlockInfo) {
    blockInfos[block] = info
    listeners.forEach { it.blockInfoUpdated(block, info) }
  }

  @RequiresEdt
  fun getBlockInfo(block: CommandBlock): CommandBlockInfo? {
    return blockInfos[block]
  }

  @RequiresEdt
  fun trimOutput() {
    val maxCapacity = getMaxCapacity()
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
    deleteDocumentRange(block, TextRange(outputStartOffset, outputStartOffset + outputLengthToRemove))
  }

  private fun findTopBlockCountToRemove(maxCapacity: Int, textLength: Int): Int {
    val firstRetainedBlockInd = blocks.indexOfFirst {
      it.endOffset + maxCapacity > textLength
    }
    return firstRetainedBlockInd.coerceAtLeast(0)
  }

  @RequiresEdt
  fun deleteDocumentRange(block: CommandBlock, deleteRange: TextRange) {
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

  private fun getMaxCapacity(): Int {
    return AdvancedSettings.getInt(NEW_TERMINAL_OUTPUT_CAPACITY_KB).coerceIn(1, 10 * 1024) * 1024
  }

  interface TerminalOutputListener {
    fun blockCreated(block: CommandBlock) {}
    fun blockRemoved(block: CommandBlock) {}

    /** Block length is finalized, so block bounds won't expand if the text is added before or after the block. */
    fun blockFinalized(block: CommandBlock) {}
    fun blockInfoUpdated(block: CommandBlock, newInfo: CommandBlockInfo) {}
  }

  companion object {
    @VisibleForTesting
    internal fun createCommandAndRightPromptText(command: String?,
                                                 prompt: PromptRenderingInfo?,
                                                 commandAttributes: TextAttributesProvider,
                                                 terminalWidth: Int): TextWithHighlightings {
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

internal class AllHighlightingsSnapshot(private val document: Document, highlightings: List<HighlightingInfo>) {
  private val allSortedHighlightings: List<HighlightingInfo> = buildAndSortHighlightings(document, highlightings)

  val size: Int
    get() = allSortedHighlightings.size

  operator fun get(index: Int): HighlightingInfo = allSortedHighlightings[index]

  /**
   * @return index of a highlighting containing the `documentOffset` (`highlighting.startOffset <= documentOffset < highlighting.endOffset`).
   *         If no such highlighting is found:
   *           - returns 0 for negative `documentOffset`
   *           - total count of highlightings for `documentOffset >= document.textLength`
   */
  fun findHighlightingIndex(documentOffset: Int): Int {
    if (documentOffset <= 0) return 0
    val binarySearchInd = allSortedHighlightings.binarySearch(0, allSortedHighlightings.size) {
      it.startOffset.compareTo(documentOffset)
    }
    return if (binarySearchInd >= 0) binarySearchInd
    else {
      val insertionIndex = -binarySearchInd - 1
      if (insertionIndex == 0 || insertionIndex == allSortedHighlightings.size && documentOffset >= document.textLength) {
        insertionIndex
      }
      else {
        insertionIndex - 1
      }
    }
  }
}

private fun buildAndSortHighlightings(document: Document, highlightings: List<HighlightingInfo>): List<HighlightingInfo> {
  val sortedHighlightings = highlightings.sortedBy { it.startOffset }
  val documentLength = document.textLength
  val result: MutableList<HighlightingInfo> = ArrayList(sortedHighlightings.size * 2 + 1)
  var startOffset = 0
  for (highlighting in sortedHighlightings) {
    if (highlighting.startOffset < 0 || highlighting.endOffset > documentLength) {
      logger<TerminalOutputModel>().error("Terminal highlightings range should be within document")
    }
    if (startOffset > highlighting.startOffset) {
      logger<TerminalOutputModel>().error("Terminal highlightings should not overlap")
    }
    if (startOffset < highlighting.startOffset) {
      result.add(HighlightingInfo(startOffset, highlighting.startOffset, EmptyTextAttributesProvider))
    }
    result.add(highlighting)
    startOffset = highlighting.endOffset
  }
  if (startOffset < documentLength) {
    result.add(HighlightingInfo(startOffset, documentLength, EmptyTextAttributesProvider))
  }
  return result
}

data class CommandBlockInfo(val exitCode: Int)

internal const val NEW_TERMINAL_OUTPUT_CAPACITY_KB: String = "new.terminal.output.capacity.kb"
