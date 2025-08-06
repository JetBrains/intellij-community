// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.FrozenDocument
import com.intellij.terminal.TerminalColorPalette
import com.intellij.terminal.session.StyleRange
import com.intellij.terminal.session.TerminalOutputModelState
import com.intellij.util.EventDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.block.output.HighlightingInfo
import org.jetbrains.plugins.terminal.block.output.TerminalOutputHighlightingsSnapshot
import org.jetbrains.plugins.terminal.block.output.TextStyleAdapter
import org.jetbrains.plugins.terminal.block.ui.BlockTerminalColorPalette
import kotlin.math.max

/**
 * [maxOutputLength] limits the length of the document. Zero means unlimited length.
 */
@ApiStatus.Internal
class TerminalOutputModelImpl(
  override val document: Document,
  private val maxOutputLength: Int,
) : TerminalOutputModel {
  private val mutableCursorOffsetState: MutableStateFlow<Int> = MutableStateFlow(0)
  override val cursorOffsetState: StateFlow<Int> = mutableCursorOffsetState.asStateFlow()

  private val highlightingsModel = HighlightingsModel()

  private val dispatcher = EventDispatcher.create(TerminalOutputModelListener::class.java)

  @VisibleForTesting
  var trimmedLinesCount: Long = 0

  @VisibleForTesting
  var trimmedCharsCount: Long = 0

  @VisibleForTesting
  var firstLineTrimmedCharsCount: Int = 0

  private var contentUpdateInProgress: Boolean = false

  private var isTypeAhead: Boolean = false

  override fun freeze(): FrozenTerminalOutputModel =
    FrozenTerminalOutputModelImpl((document as DocumentImpl).freeze(), trimmedCharsCount, trimmedLinesCount, cursorOffsetState.value)

  override fun relativeOffset(offset: Int): TerminalOffset = TerminalOffsetImpl(trimmedCharsCount, offset)

  override fun absoluteOffset(offset: Long): TerminalOffset = TerminalOffsetImpl(trimmedCharsCount, (offset - trimmedCharsCount).toInt())

  override fun relativeLine(line: Int): TerminalLine = TerminalLineImpl(trimmedLinesCount, line)

  override fun absoluteLine(line: Long): TerminalLine = TerminalLineImpl(trimmedLinesCount, (line - trimmedLinesCount).toInt())

  override fun getAbsoluteLineIndex(documentOffset: Int): Long {
    val documentLineIndex = document.getLineNumber(documentOffset)
    return trimmedLinesCount + documentLineIndex.toLong()
  }

  override fun updateContent(absoluteLineIndex: Long, text: String, styles: List<StyleRange>) {
    changeDocumentContent {
      // If absolute line index is far in the past - in the already trimmed part of the output,
      // then it means that the terminal was cleared, and we should reset to the initial state.
      if (absoluteLineIndex < trimmedLinesCount) {
        trimmedLinesCount = 0
        trimmedCharsCount = 0
        firstLineTrimmedCharsCount = 0
      }

      val documentLineIndex = (absoluteLineIndex - trimmedLinesCount).toInt()
      doUpdateContent(documentLineIndex, text, styles)
    }
  }

  override fun replaceContent(offset: TerminalOffset, length: Int, text: String, newStyles: List<StyleRange>) {
    changeDocumentContent(isTypeAhead) { 
      val relativeStartOffset = offset.toRelative()
      doReplaceContent(relativeStartOffset, length, text, newStyles)
    }
  }

  override fun updateCursorPosition(absoluteLineIndex: Long, columnIndex: Int) {
    val documentLineIndex = (absoluteLineIndex - trimmedLinesCount).toInt()
    LOG.debug {
      "Updating the cursor position to absolute line = $absoluteLineIndex (relative $documentLineIndex), " +
      "column = $columnIndex"
    }
    ensureDocumentHasLine(documentLineIndex)
    val lineStartOffset = document.getLineStartOffset(documentLineIndex)
    val lineEndOffset = document.getLineEndOffset(documentLineIndex)
    val trimmedCharsInLine = if (documentLineIndex == 0) firstLineTrimmedCharsCount else 0
    // columnIndex comes from the backend model, which doesn't know about trimming,
    // so for the first line the index may be off, we need to apply correction
    val trimmedColumnIndex = columnIndex - trimmedCharsInLine
    val lineLength = lineEndOffset - lineStartOffset

    // Add spaces to the line if the cursor position is out of line bounds
    if (trimmedColumnIndex > lineLength) {
      val spacesToAdd = trimmedColumnIndex - lineLength
      val spaces = " ".repeat(spacesToAdd)
      changeDocumentContent {
        document.insertString(lineEndOffset, spaces)
        LOG.debug { "Added $spacesToAdd spaces to make the column valid" }
        highlightingsModel.insertEmptyHighlightings(lineEndOffset, spacesToAdd)
        lineEndOffset
      }
    }

    val newCursorOffset = lineStartOffset + trimmedColumnIndex
    LOG.debug { "Updated the cursor position to $newCursorOffset" }
    mutableCursorOffsetState.value = newCursorOffset
  }

  override fun updateCursorPosition(offset: TerminalOffset) {
    mutableCursorOffsetState.value = offset.toRelative()
  }

  /** Returns offset from which document was updated */
  private fun doUpdateContent(documentLineIndex: Int, text: String, styles: List<StyleRange>): Int {
    LOG.debug {
      "Content update from the relative line = $documentLineIndex (absolute ${documentLineIndex + trimmedLinesCount}), " +
      "length = ${text.length}, " +
      "current length = ${document.textLength} chars, ${document.lineCount} lines, " +
      "currently trimmed = $trimmedCharsCount chars, $trimmedLinesCount lines"
    }
    ensureDocumentHasLine(documentLineIndex)

    val replaceStartOffset = document.getLineStartOffset(documentLineIndex)
    document.replaceString(replaceStartOffset, document.textLength, text)
    ensureCorrectCursorOffset()

    highlightingsModel.removeAfter(replaceStartOffset)
    highlightingsModel.addHighlightings(replaceStartOffset, styles)

    val trimmedCount = trimToSize()

    LOG.debug {
      "Content updated from relative offset = $replaceStartOffset, " +
      "new length = ${document.textLength} chars, ${document.lineCount} lines, " +
      "currently trimmed = $trimmedCharsCount chars, $trimmedLinesCount lines"
    }

    return max(0, replaceStartOffset - trimmedCount)
  }

  private fun ensureDocumentHasLine(documentLineIndex: Int) {
    if (documentLineIndex > 0 && documentLineIndex >= document.lineCount) {
      val newLinesToAdd = documentLineIndex - document.lineCount + 1
      val newLines = "\n".repeat(newLinesToAdd)
      document.insertString(document.textLength, newLines)
      LOG.debug { "Added $newLinesToAdd lines to make the line valid" }
    }
  }

  /** Returns offset from which document was updated */
  private fun doReplaceContent(relativeStartOffset: Int, length: Int, text: String, styles: List<StyleRange>): Int {
    val relativeEndOffset = relativeStartOffset + length
    document.replaceString(relativeStartOffset, relativeEndOffset, text)
    highlightingsModel.updateHighlightings(relativeStartOffset, length, text.length, styles)
    val trimmedCount = trimToSize()
    ensureCorrectCursorOffset()
    return max(0, relativeStartOffset - trimmedCount)
  }

  private fun ensureCorrectCursorOffset() {
    // If the document became shorter, immediately ensure that the cursor is still within the document.
    // It'll update itself later to the correct position anyway, but having the incorrect value can cause exceptions before that.
    val newLength = document.textLength
    if (mutableCursorOffsetState.value > newLength) {
      mutableCursorOffsetState.value = newLength
    }
  }

  /** Returns trimmed characters count */
  private fun trimToSize(): Int {
    return if (maxOutputLength > 0 && document.textLength > maxOutputLength) {
      trimToSize(maxOutputLength)
    }
    else 0
  }

  /** Returns trimmed characters count */
  private fun trimToSize(maxLength: Int): Int {
    val textLength = document.textLength
    check(textLength > maxLength) { "This method should be called only if text length $textLength is greater than max length $maxLength" }

    val lineCountBefore = document.lineCount
    val removeUntilOffset = textLength - maxLength
    val futureFirstLineNumber = document.getLineNumber(removeUntilOffset)
    val futureFirstLineStart = document.getLineStartOffset(futureFirstLineNumber)
    document.deleteString(0, removeUntilOffset)

    highlightingsModel.removeBefore(removeUntilOffset)

    trimmedCharsCount += removeUntilOffset
    trimmedLinesCount += lineCountBefore - document.lineCount
    firstLineTrimmedCharsCount = removeUntilOffset - futureFirstLineStart

    return removeUntilOffset
  }

  /**
   * Document changes in this model are allowed only inside [block] of this function.
   * [block] should return an offset from which document content was changed.
   */
  private fun changeDocumentContent(isTypeAhead: Boolean = false, block: () -> Int) {
    dispatcher.multicaster.beforeContentChanged(this)

    contentUpdateInProgress = true
    val changeStartOffset = try {
      block()
    }
    finally {
      contentUpdateInProgress = false
    }

    dispatcher.multicaster.afterContentChanged(this, changeStartOffset, isTypeAhead)
  }

  override fun getHighlightings(): TerminalOutputHighlightingsSnapshot {
    // Highlightings can be requested by the listeners of document change events during content updating.
    // But highlightings may be not in sync with the document content, so it may cause exceptions.
    // Also, there is no sense to provide a real highlighting until the update is finished, so return an empty snapshot in this case.
    return if (contentUpdateInProgress) {
      TerminalOutputHighlightingsSnapshot(document, emptyList())
    }
    else highlightingsModel.getHighlightingsSnapshot()
  }

  override fun getHighlightingAt(documentOffset: Int): HighlightingInfo? {
    return highlightingsModel.getHighlightingAt(documentOffset)
  }

  override fun addListener(parentDisposable: Disposable, listener: TerminalOutputModelListener) {
    dispatcher.addListener(listener, parentDisposable)
  }

  override fun withTypeAhead(block: () -> Unit) {
    check(!isTypeAhead) { "Already in the type-ahead mode" }
    isTypeAhead = true
    try {
      block()
    }
    finally {
      isTypeAhead = false
    }
  }

  override fun dumpState(): TerminalOutputModelState {
    return TerminalOutputModelState(
      text = document.text,
      trimmedLinesCount = trimmedLinesCount,
      trimmedCharsCount = trimmedCharsCount,
      firstLineTrimmedCharsCount = firstLineTrimmedCharsCount,
      cursorOffset = cursorOffsetState.value,
      highlightings = highlightingsModel.dumpState()
    )
  }

  override fun restoreFromState(state: TerminalOutputModelState) {
    changeDocumentContent {
      trimmedLinesCount = state.trimmedLinesCount
      trimmedCharsCount = state.trimmedCharsCount
      firstLineTrimmedCharsCount = state.firstLineTrimmedCharsCount
      document.setText(state.text)
      highlightingsModel.restoreFromState(state.highlightings)
      mutableCursorOffsetState.value = state.cursorOffset

      0  // the document is changed from right from the start
    }
  }

  private inner class HighlightingsModel {
    private val colorPalette: TerminalColorPalette = BlockTerminalColorPalette()

    /**
     * Contains sorted ranges of the text that are highlighted differently than default.
     * Indexes of the ranges are absolute to support trimming the start of the list
     * without reassigning indexes for the remaining ranges: [removeBefore].
     */
    private val styleRanges: MutableList<StyleRange> = ArrayDeque() // ArrayDeque is used here for fast removeAt(0).

    /**
     * Contains sorted ranges of the highlightings that cover all document length.
     * Indexes of the ranges are document-relative, so the first range always starts with 0.
     */
    private var highlightingsSnapshot: TerminalOutputHighlightingsSnapshot? = null

    fun getHighlightingsSnapshot(): TerminalOutputHighlightingsSnapshot {
      if (highlightingsSnapshot != null) {
        return highlightingsSnapshot!!
      }

      val documentRelativeHighlightings = styleRanges.map {
        HighlightingInfo(
          startOffset = (it.startOffset - trimmedCharsCount).toInt(),
          endOffset = (it.endOffset - trimmedCharsCount).toInt(),
          textAttributesProvider = TextStyleAdapter(it.style, colorPalette),
        )
      }
      val snapshot = TerminalOutputHighlightingsSnapshot(document, documentRelativeHighlightings)
      highlightingsSnapshot = snapshot
      return snapshot
    }

    fun getHighlightingAt(documentOffset: Int): HighlightingInfo? {
      if (documentOffset < 0 || documentOffset >= document.textLength) {
        return null
      }
      val absoluteOffset = documentOffset + trimmedCharsCount
      val index = styleRanges.binarySearch {
        when {
          it.endOffset <= absoluteOffset -> -1
          it.startOffset > absoluteOffset -> 1
          else -> 0
        }
      }
      return if (index >= 0) {
        val range = styleRanges[index]
        HighlightingInfo(
          startOffset = (range.startOffset - trimmedCharsCount).toInt(),
          endOffset = (range.endOffset - trimmedCharsCount).toInt(),
          textAttributesProvider = TextStyleAdapter(range.style, colorPalette),
        )
      }
      else null
    }

    fun addHighlightings(documentOffset: Int, styles: List<StyleRange>) {
      val absoluteOffset = documentOffset + trimmedCharsCount

      check(styleRanges.isEmpty() || styleRanges.last().endOffset <= absoluteOffset) { "New highlightings overlap with existing" }

      val adjustedStyles = styles.map {
        StyleRange(absoluteOffset + it.startOffset, absoluteOffset + it.endOffset, it.style)
      }
      styleRanges.addAll(adjustedStyles)

      highlightingsSnapshot = null
    }

    /**
     * Moves all highlightings that start after [documentOffset] by [length].
     * If [documentOffset] is inside some highlighting, this highlighting won't be changed.
     */
    fun insertEmptyHighlightings(documentOffset: Int, length: Int) {
      val absoluteOffset = documentOffset + trimmedCharsCount

      val styleIndex = styleRanges.binarySearch { it.startOffset.compareTo(absoluteOffset) }
      val updateFromIndex = if (styleIndex < 0) -styleIndex - 1 else styleIndex

      if (updateFromIndex < styleRanges.size) {
        for (ind in (updateFromIndex until styleRanges.size)) {
          val cur = styleRanges[ind]
          styleRanges[ind] = StyleRange(cur.startOffset + length, cur.endOffset + length, cur.style)
        }

        highlightingsSnapshot = null
      }
    }

    fun removeAfter(documentOffset: Int) {
      val absoluteOffset = documentOffset + trimmedCharsCount
      val styleIndex = styleRanges.binarySearch { it.endOffset.compareTo(absoluteOffset) }
      val removeFromIndex = if (styleIndex < 0) -styleIndex - 1 else styleIndex + 1
      for (ind in (styleRanges.size - 1) downTo removeFromIndex) {
        styleRanges.removeAt(ind)
      }

      highlightingsSnapshot = null
    }

    fun removeBefore(documentOffset: Int) {
      val absoluteOffset = documentOffset + trimmedCharsCount
      val styleIndex = styleRanges.binarySearch { it.startOffset.compareTo(absoluteOffset) }
      val removeUntilHighlightingIndex = if (styleIndex < 0) -styleIndex - 1 else styleIndex
      repeat(removeUntilHighlightingIndex) {
        styleRanges.removeAt(0)
      }

      highlightingsSnapshot = null
    }

    fun updateHighlightings(relativeStartOffset: Int, oldLength: Int, newLength: Int, styles: List<StyleRange>) {
      val absoluteStartOffset = relativeStartOffset + trimmedCharsCount
      val absoluteEndOffset = absoluteStartOffset + oldLength
      val lastUnaffectedIndexBefore = styleRanges.binarySearch { it.endOffset.compareTo(absoluteStartOffset) }.let { i ->
        if (i >= 0) i else -i - 2
      }
      val firstUnaffectedIndexAfter = styleRanges.binarySearch { it.startOffset.compareTo(absoluteEndOffset) }.let { i ->
        if (i >= 0) i else -i - 1
      }
      val shift = newLength - oldLength

      shift(firstUnaffectedIndexAfter, shift)

      updateAffectedRanges(
        affectedIndexes = lastUnaffectedIndexBefore + 1 until firstUnaffectedIndexAfter,
        affectedAbsoluteOffsets = absoluteStartOffset until absoluteEndOffset,
        shift = shift,
      )
      
      // We could've calculated it in updateAffectedRanges, but it's error-prone and fragile,
      // it's much easier to just look it up.
      val insertionIndex = styleRanges.binarySearch { it.startOffset.compareTo(absoluteEndOffset + shift) }.let { i ->
        if (i >= 0) i else -i - 1
      }
      val absoluteStyles = styles.map { styleRange -> 
        styleRange.copy(
          startOffset = styleRange.startOffset + absoluteStartOffset,
          endOffset = styleRange.endOffset + absoluteStartOffset,
        )
      }
      styleRanges.addAll(insertionIndex, absoluteStyles)
    }

    private fun shift(shiftFromIndex: Int, shift: Int) {
      if (shift == 0) return
      for (i in shiftFromIndex until styleRanges.size) {
        val styleRange = styleRanges[i]
        styleRanges[i] = styleRange.copy(
          startOffset = styleRange.startOffset + shift,
          endOffset = styleRange.endOffset + shift,
        )
      }
    }

    private fun updateAffectedRanges(affectedIndexes: IntRange, affectedAbsoluteOffsets: LongRange, shift: Int) {
      if (affectedIndexes.isEmpty()) return // affectedAbsoluteOffsets might be empty in case of an insertion, but we still need to split then
      val absoluteStartOffset = affectedAbsoluteOffsets.first
      val absoluteEndOffset = affectedAbsoluteOffsets.last + 1
      val affectedRanges = styleRanges.subList(affectedIndexes.first, affectedIndexes.last + 1)
      val updatedRanges = mutableListOf<StyleRange>()
      for (range in affectedRanges) {
        when {
          range.startOffset < absoluteStartOffset && range.endOffset <= absoluteEndOffset -> {
            // the start of the range is retained, the end is trimmed
            updatedRanges.add(range.copy(endOffset = absoluteStartOffset))
          }
          range.startOffset in affectedAbsoluteOffsets && range.endOffset > absoluteEndOffset -> {
            // the start of the range is trimmed, the end is retained, the range is shifted
            updatedRanges.add(range.copy(startOffset = absoluteEndOffset + shift, endOffset = range.endOffset + shift))
          }
          range.startOffset < absoluteStartOffset && range.endOffset > absoluteEndOffset -> {
            // the range is split, both parts are trimmed, the right part is also shifted
            updatedRanges.add(range.copy(endOffset = absoluteStartOffset))
            updatedRanges.add(range.copy(startOffset = absoluteEndOffset + shift, endOffset = range.endOffset + shift))
          }
          // else the entire range is inside the removed range and therefore is removed
        }
      }
      affectedRanges.clear()
      affectedRanges.addAll(updatedRanges)
    }

    fun dumpState(): List<StyleRange> {
      return styleRanges.toList()
    }

    fun restoreFromState(state: List<StyleRange>) {
      styleRanges.clear()
      styleRanges.addAll(state)

      highlightingsSnapshot = null
    }
  }
}

@ApiStatus.Internal
class FrozenTerminalOutputModelImpl(
  override val document: FrozenDocument,
  private val trimmedCharsCount: Long,
  private val trimmedLinesCount: Long,
  override val cursorOffset: Int,
) : FrozenTerminalOutputModel {
  override fun relativeOffset(offset: Int): TerminalOffset = TerminalOffsetImpl(trimmedCharsCount, offset)
  override fun absoluteOffset(offset: Long): TerminalOffset = TerminalOffsetImpl(trimmedCharsCount, (offset - trimmedCharsCount).toInt())
  override fun relativeLine(line: Int): TerminalLine = TerminalLineImpl(trimmedLinesCount, line)
  override fun absoluteLine(line: Long): TerminalLine = TerminalLineImpl(trimmedLinesCount, (line - trimmedLinesCount).toInt())
}

private data class TerminalOffsetImpl(
  val trimmedCharsCount: Long,
  val relative: Int,
) : TerminalOffset {
  override fun compareTo(other: TerminalOffset): Int = toAbsolute().compareTo(other.toAbsolute())
  override fun toAbsolute(): Long = trimmedCharsCount + relative
  override fun toRelative(): Int = relative
  override fun toString(): String = "${toAbsolute()}(${toRelative()})"
}

private data class TerminalLineImpl(
  val trimmedLinesCount: Long,
  val relative: Int,
) : TerminalLine {
  override fun compareTo(other: TerminalLine): Int = toAbsolute().compareTo(other.toAbsolute())
  override fun toAbsolute(): Long = trimmedLinesCount + relative
  override fun toRelative(): Int = relative
  override fun toString(): String = "${toAbsolute()}(${toRelative()})"
}

private val LOG = logger<TerminalOutputModelImpl>()
