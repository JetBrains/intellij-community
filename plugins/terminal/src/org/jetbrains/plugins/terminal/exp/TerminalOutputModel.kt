// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.RangeMarkerImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.Rectangle
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max
import kotlin.math.min

class TerminalOutputModel(val editor: EditorEx) {
  private val blocks: MutableList<CommandBlock> = Collections.synchronizedList(ArrayList())
  private val decorations: MutableMap<CommandBlock, BlockDecoration> = HashMap()
  private val highlightings: MutableMap<CommandBlock, List<HighlightingInfo>> = LinkedHashMap()  // order matters
  private val blockStates: MutableMap<CommandBlock, List<BlockDecorationState>> = HashMap()
  private var allHighlightingsSnapshot: AllHighlightingsSnapshot? = null

  private val document: Document = editor.document
  private val listeners: MutableList<TerminalOutputListener> = CopyOnWriteArrayList()

  fun addListener(listener: TerminalOutputListener, disposable: Disposable? = null) {
    listeners.add(listener)
    if (disposable != null) {
      Disposer.register(disposable) { listeners.remove(listener) }
    }
  }

  @RequiresEdt
  fun createBlock(command: String?, prompt: PromptRenderingInfo?): CommandBlock {
    closeLastBlock()

    if (document.textLength > 0) {
      document.insertString(document.textLength, "\n")
    }
    val startOffset = document.textLength
    val marker = document.createRangeMarker(startOffset, startOffset)
    marker.isGreedyToRight = true

    val adjustedPrompt = prompt?.let { p ->
      val highlightings = p.highlightings.map {
        HighlightingInfo(startOffset + it.startOffset, startOffset + it.endOffset, it.textAttributes)
      }
      PromptRenderingInfo(p.text, highlightings)
    }
    val block = CommandBlock(command, adjustedPrompt, marker)
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
    val startBlockInd = blocks.indexOf(block)
    check(startBlockInd >= 0)
    for (blockInd in startBlockInd + 1 until blocks.size) {
      deleteDocumentRangeInHighlightings(blocks[blockInd], TextRange(block.startOffset, block.endOffset))
    }

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
    allHighlightingsSnapshot = null
    blockStates.remove(block)

    // Remove the text after removing the highlightings because removing text will trigger rehighlight
    // and there should be no highlightings at this moment.
    document.deleteString(block.startOffset, block.endOffset)
    block.range.dispose()
  }

  @RequiresEdt
  fun clearBlocks() {
    val blocksCopy = blocks.reversed()
    for (block in blocksCopy) {
      removeBlock(block)
    }
    editor.document.setText("")
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
  internal fun getHighlightingsSnapshot(): AllHighlightingsSnapshot {
    var snapshot: AllHighlightingsSnapshot? = allHighlightingsSnapshot
    if (snapshot == null) {
      snapshot = AllHighlightingsSnapshot(editor.document, highlightings.flatMap { it.value })
      allHighlightingsSnapshot = snapshot
    }
    return snapshot
  }

  @RequiresEdt
  fun getHighlightings(block: CommandBlock): List<HighlightingInfo>? {
    return highlightings[block]
  }

  @RequiresEdt
  fun putHighlightings(block: CommandBlock, highlightings: List<HighlightingInfo>) {
    this.highlightings[block] = highlightings
    allHighlightingsSnapshot = null
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

  @RequiresEdt
  fun trimOutput() {
    val maxCapacity = AdvancedSettings.getInt("terminal.editor.max.text.length")
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
    val highlightings = getHighlightings(block) ?: emptyList()
    val updatedHighlightings: List<HighlightingInfo> = highlightings.mapNotNull {
      when {
        it.endOffset <= deleteRange.startOffset -> it
        findIntersectionLength(it, deleteRange) > 0 -> {
          val intersectionLength = findIntersectionLength(it, deleteRange)
          val newRangeStart = min(it.startOffset, deleteRange.startOffset)
          val newRangeEnd = newRangeStart + it.length() - intersectionLength
          if (newRangeStart != newRangeEnd)
            HighlightingInfo(newRangeStart, newRangeEnd, it.textAttributes)
          else
            null // the whole highlighting is deleted
        }
        else -> {
          check(it.startOffset >= deleteRange.endOffset)
          val newRangeStart = it.startOffset - deleteRange.length
          HighlightingInfo(newRangeStart, newRangeStart + it.length(), it.textAttributes)
        }
      }
    }
    putHighlightings(block, updatedHighlightings)
  }

  private fun findIntersectionLength(range1: HighlightingInfo, range2: TextRange): Int {
    val intersectionLength = min(range1.endOffset, range2.endOffset) - max(range1.startOffset, range2.startOffset)
    return max(intersectionLength, 0)
  }

  interface TerminalOutputListener {
    fun blockRemoved(block: CommandBlock) {}
    fun blockDecorationStateChanged(block: CommandBlock, oldStates: List<BlockDecorationState>, newStates: List<BlockDecorationState>) {}
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
    val searchKey = HighlightingInfo(documentOffset, documentOffset, TextAttributes.ERASE_MARKER)
    val binarySearchInd = Collections.binarySearch(allSortedHighlightings, searchKey) { a, b ->
      a.startOffset.compareTo(b.startOffset)
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
      result.add(HighlightingInfo(startOffset, highlighting.startOffset, TextAttributes.ERASE_MARKER))
    }
    result.add(highlighting)
    startOffset = highlighting.endOffset
  }
  if (startOffset < documentLength) {
    result.add(HighlightingInfo(startOffset, documentLength, TextAttributes.ERASE_MARKER))
  }
  return result
}

data class CommandBlock(val command: String?, val prompt: PromptRenderingInfo?, val range: RangeMarker) {
  val startOffset: Int
    get() = range.startOffset
  val endOffset: Int
    get() = range.endOffset
  val commandStartOffset: Int
    get() = range.startOffset + if (withPrompt) prompt!!.text.length + 1 else 0
  val outputStartOffset: Int
    get() = commandStartOffset + if (withCommand) command!!.length + 1 else 0
  val textRange: TextRange
    get() = range.textRange

  val withPrompt: Boolean = !prompt?.text.isNullOrEmpty()
  val withCommand: Boolean = !command.isNullOrEmpty()
}

internal const val TERMINAL_EDITOR_MAX_TEXT_LENGTH: String = "terminal.editor.max.text.length"
