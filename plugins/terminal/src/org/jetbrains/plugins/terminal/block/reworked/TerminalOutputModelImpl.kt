// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
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

  override fun updateCursorPosition(absoluteLineIndex: Long, columnIndex: Int) {
    val documentLineIndex = (absoluteLineIndex - trimmedLinesCount).toInt()
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
        highlightingsModel.insertEmptyHighlightings(lineEndOffset, spacesToAdd)
        lineEndOffset
      }
    }

    mutableCursorOffsetState.value = lineStartOffset + trimmedColumnIndex
  }

  /** Returns offset from which document was updated */
  private fun doUpdateContent(documentLineIndex: Int, text: String, styles: List<StyleRange>): Int {
    if (documentLineIndex > 0 && documentLineIndex >= document.lineCount) {
      val newLines = "\n".repeat(documentLineIndex - document.lineCount + 1)
      document.insertString(document.textLength, newLines)
    }

    val replaceStartOffset = document.getLineStartOffset(documentLineIndex)
    document.replaceString(replaceStartOffset, document.textLength, text)
    // If the document became shorter, immediately ensure that the cursor is still within the document.
    // It'll update itself later to the correct position anyway, but having the incorrect value can cause exceptions before that.
    val newLength = document.textLength
    if (mutableCursorOffsetState.value > newLength) {
      mutableCursorOffsetState.value = newLength
    }

    highlightingsModel.removeAfter(replaceStartOffset)
    highlightingsModel.addHighlightings(replaceStartOffset, styles)

    val trimmedCount = trimToSize()

    return max(0, replaceStartOffset - trimmedCount)
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
  private fun changeDocumentContent(block: () -> Int) {
    dispatcher.multicaster.beforeContentChanged(this)

    contentUpdateInProgress = true
    val changeStartOffset = try {
      block()
    }
    finally {
      contentUpdateInProgress = false
    }

    dispatcher.multicaster.afterContentChanged(this, changeStartOffset)
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

  override fun addListener(parentDisposable: Disposable, listener: TerminalOutputModelListener) {
    dispatcher.addListener(listener, parentDisposable)
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
    private val styleRanges: MutableList<StyleRange> = ArrayDeque()

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