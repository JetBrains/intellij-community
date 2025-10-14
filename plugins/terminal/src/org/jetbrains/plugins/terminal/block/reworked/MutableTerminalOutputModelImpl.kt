// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.FrozenDocument
import com.intellij.openapi.util.TextRange
import com.intellij.terminal.TerminalColorPalette
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.block.output.HighlightingInfo
import org.jetbrains.plugins.terminal.block.output.TerminalOutputHighlightingsSnapshot
import org.jetbrains.plugins.terminal.block.output.TextStyleAdapter
import org.jetbrains.plugins.terminal.block.ui.BlockTerminalColorPalette
import org.jetbrains.plugins.terminal.session.StyleRange
import org.jetbrains.plugins.terminal.session.TerminalOutputModelState

/**
 * [maxOutputLength] limits the length of the document. Zero means unlimited length.
 */
@ApiStatus.Internal
class MutableTerminalOutputModelImpl(
  override val document: Document,
  private val maxOutputLength: Int,
) : MutableTerminalOutputModel {

  override var cursorOffset: TerminalOffset = TerminalOffset.ZERO

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

  override val immutableText: CharSequence
    get() = immutableTextImpl(document)

  override val lineCount: Int
    get() = lineCountImpl(document)

  override val modificationStamp: Long
    get() = modificationStampImpl(document)

  override val startOffset: TerminalOffset
    get() = TerminalOffset.of(trimmedCharsCount)

  override val firstLine: TerminalLineIndex
    get() = TerminalLineIndex.of(trimmedLinesCount)

  private fun relativeOffset(offset: Int): TerminalOffset =
    TerminalOffset.of(trimmedCharsCount + offset)

  override fun getLineByOffset(offset: TerminalOffset): TerminalLineIndex =
    getLineByOffsetImpl(trimmedLinesCount, document, offset.toRelative())

  override fun getStartOfLine(line: TerminalLineIndex): TerminalOffset =
    getStartOfLineImpl(trimmedCharsCount, document, line.toRelative())

  override fun getEndOfLine(line: TerminalLineIndex, includeEOL: Boolean): TerminalOffset =
    getEndOfLineImpl(trimmedCharsCount, document, line.toRelative(), includeEOL)

  override fun getText(start: TerminalOffset, end: TerminalOffset): String =
    getTextImpl(document, start.toRelative(), end.toRelative())

  private fun TerminalOffset.toRelative(): Int = (this - startOffset).toInt()

  private fun TerminalLineIndex.toRelative(): Int = (this - firstLine).toInt()

  override fun takeSnapshot(): TerminalOutputModelSnapshot =
    TerminalOutputModelSnapshotImpl((document as DocumentImpl).freeze(), trimmedCharsCount, trimmedLinesCount, cursorOffset)

  override fun updateContent(absoluteLineIndex: Long, text: String, styles: List<StyleRange>) {
    // If absolute line index is far in the past - in the already trimmed part of the output,
    // then it means that the terminal was cleared, and we should reset to the initial state.
    if (absoluteLineIndex < trimmedLinesCount) {
      changeDocumentContent {
        trimmedLinesCount = 0
        trimmedCharsCount = 0
        firstLineTrimmedCharsCount = 0
        doReplaceContent(TerminalOffset.ZERO, textLength, "", emptyList())
      }
    }

    LOG.debug {
      "Content update from the relative line = ${absoluteLineIndex - trimmedLinesCount} (absolute ${absoluteLineIndex}), " +
      "length = ${text.length}, " +
      "current length = ${document.textLength} chars, ${document.lineCount} lines, " +
      "currently trimmed = $trimmedCharsCount chars, $trimmedLinesCount lines"
    }

    val startLine = TerminalLineIndex.of(absoluteLineIndex)
    ensureDocumentHasLine(startLine)
    changeDocumentContent {
      val startOffset = getStartOfLine(startLine)
      doReplaceContent(startOffset, (endOffset - startOffset).toInt(), text, styles)
    }
  }

  override fun replaceContent(offset: TerminalOffset, length: Int, text: String, newStyles: List<StyleRange>) {
    changeDocumentContent(isTypeAhead) { 
      doReplaceContent(offset, length, text, newStyles)
    }
  }

  override fun updateCursorPosition(absoluteLineIndex: Long, columnIndex: Int) {
    val lineIndex = TerminalLineIndex.of(absoluteLineIndex)
    LOG.debug {
      "Updating the cursor position to absolute line = $absoluteLineIndex (relative ${lineIndex.toRelative()}), " +
      "column = $columnIndex"
    }
    ensureDocumentHasLine(lineIndex)
    val lineStartOffset = getStartOfLine(lineIndex)
    val lineEndOffset = getEndOfLine(lineIndex)
    val trimmedCharsInLine = if (lineIndex == firstLine) firstLineTrimmedCharsCount else 0
    // columnIndex comes from the backend model, which doesn't know about trimming,
    // so for the first line the index may be off, we need to apply correction
    val trimmedColumnIndex = columnIndex - trimmedCharsInLine
    val lineLength = (lineEndOffset - lineStartOffset).toInt()

    // Add spaces to the line if the cursor position is out of line bounds
    if (trimmedColumnIndex > lineLength) {
      val spacesToAdd = trimmedColumnIndex - lineLength
      val spaces = " ".repeat(spacesToAdd)
      changeDocumentContent {
        LOG.debug { "Added $spacesToAdd spaces to make the column valid" }
        doReplaceContent(lineEndOffset, 0, spaces, emptyList())
      }
    }

    val newCursorOffset = lineStartOffset + trimmedColumnIndex.toLong()
    LOG.debug { "Updated the cursor position to $newCursorOffset" }
    updateCursorPosition(newCursorOffset)
  }

  override fun updateCursorPosition(offset: TerminalOffset) {
    val oldValue = cursorOffset
    this.cursorOffset = offset
    dispatcher.multicaster.cursorOffsetChanged(TerminalCursorOffsetChangedImpl(this, oldValue, offset))
  }

  private fun ensureDocumentHasLine(lineIndex: TerminalLineIndex) {
    if (lineIndex > lastLine) {
      changeDocumentContent {
        val newLinesToAdd = (lineIndex - lastLine).toInt()
        val newLines = "\n".repeat(newLinesToAdd)
        LOG.debug { "Add $newLinesToAdd lines to make the line valid" }
        doReplaceContent(endOffset, 0, newLines, emptyList())
      }
    }
  }

  /** Returns offset from which document was updated */
  private fun doReplaceContent(startOffset: TerminalOffset, length: Int, text: String, styles: List<StyleRange>): ModelChange {
    val relativeStartOffset = startOffset.toRelative()
    val relativeEndOffset = relativeStartOffset + length
    val oldText = document.immutableCharSequence.subSequence(relativeStartOffset, relativeEndOffset)
    document.replaceString(relativeStartOffset, relativeEndOffset, text)
    highlightingsModel.updateHighlightings(relativeStartOffset, length, text.length, styles)
    ensureCorrectCursorOffset()
    return ModelChange(
      startOffset,
      oldText,
      text
    )
  }

  private fun ensureCorrectCursorOffset() {
    // If the document became shorter, immediately ensure that the cursor is still within the document.
    // It'll update itself later to the correct position anyway, but having the incorrect value can cause exceptions before that.
    val newLength = document.textLength
    val docEndOffset = relativeOffset(newLength)
    if (cursorOffset > docEndOffset) {
      updateCursorPosition(docEndOffset)
    }
  }

  /** Returns trimmed characters count */
  private fun trimToSize(): CharSequence {
    return if (maxOutputLength > 0 && document.textLength > maxOutputLength) {
      trimToSize(maxOutputLength)
    }
    else ""
  }

  /** Returns trimmed characters count */
  private fun trimToSize(maxLength: Int): CharSequence {
    val textLength = document.textLength
    check(textLength > maxLength) { "This method should be called only if text length $textLength is greater than max length $maxLength" }

    val lineCountBefore = document.lineCount
    val removeUntilOffset = textLength - maxLength
    val futureFirstLineNumber = document.getLineNumber(removeUntilOffset)
    val futureFirstLineStart = document.getLineStartOffset(futureFirstLineNumber)

    highlightingsModel.removeBefore(removeUntilOffset)

    // TODO: TerminalBlocksModelImpl uses a document listener and relies on this value being already updated, need to migrate it to a model listener
    trimmedCharsCount += removeUntilOffset

    val trimmedPart = document.immutableCharSequence.subSequence(0, removeUntilOffset)
    document.deleteString(0, removeUntilOffset)

    trimmedLinesCount += lineCountBefore - document.lineCount
    firstLineTrimmedCharsCount = removeUntilOffset - futureFirstLineStart

    return trimmedPart
  }

  /**
   * Document changes in this model are allowed only inside [block] of this function.
   * [block] should return an offset from which document content was changed.
   */
  private fun changeDocumentContent(isTypeAhead: Boolean = false, block: () -> ModelChange) {
    dispatcher.multicaster.beforeContentChanged(this)

    check(!contentUpdateInProgress) { "Recursive content updates aren't supported, schedule an update in a separate event if needed" }
    contentUpdateInProgress = true
    val change = try {
      block()
    }
    finally {
      contentUpdateInProgress = false
    }

    dispatcher.multicaster.afterContentChanged(TerminalContentChangedImpl(
      this,
      change.offset,
      change.oldText,
      change.newText,
      isTypeAhead,
      false
    ))

    val startBeforeTrimming = startOffset
    val trimmedSequence = trimToSize()

    if (trimmedSequence.isNotEmpty()) {
      dispatcher.multicaster.afterContentChanged(TerminalContentChangedImpl(
        this,
        startBeforeTrimming,
        trimmedSequence,
        "",
        isTypeAhead,
        true
      ))
    }

    val effectiveStartOffset = change.offset.coerceAtLeast(startOffset)

    dispatcher.multicaster.afterContentChanged(this, effectiveStartOffset, isTypeAhead)

    LOG.debug {
      "Content updated from offset = $effectiveStartOffset, " +
      "new length = ${document.textLength} chars, ${document.lineCount} lines, " +
      "currently trimmed = $trimmedCharsCount (+${trimmedSequence.length}) chars, $trimmedLinesCount lines"
    }
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

  override fun getHighlightingAt(documentOffset: TerminalOffset): HighlightingInfo? {
    return highlightingsModel.getHighlightingAt(documentOffset.toRelative())
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
      cursorOffset = cursorOffset.toRelative(),
      highlightings = highlightingsModel.dumpState()
    )
  }

  override fun restoreFromState(state: TerminalOutputModelState) {
    changeDocumentContent {
      val oldText = immutableText
      trimmedLinesCount = state.trimmedLinesCount
      trimmedCharsCount = state.trimmedCharsCount
      firstLineTrimmedCharsCount = state.firstLineTrimmedCharsCount
      document.setText(state.text)
      highlightingsModel.restoreFromState(state.highlightings)
      updateCursorPosition(relativeOffset(state.cursorOffset))

      ModelChange(startOffset, oldText, state.text)  // the document is changed from right from the start
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
          textAttributesProvider = TextStyleAdapter(it.style, colorPalette, it.ignoreContrastAdjustment),
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
          textAttributesProvider = TextStyleAdapter(range.style, colorPalette, range.ignoreContrastAdjustment),
        )
      }
      else null
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

private data class ModelChange(
  val offset: TerminalOffset,
  val oldText: CharSequence,
  val newText: CharSequence,
)

@ApiStatus.Internal
class TerminalOutputModelSnapshotImpl(
  private val document: FrozenDocument,
  private val trimmedCharsCount: Long,
  private val trimmedLinesCount: Long,
  override val cursorOffset: TerminalOffset,
) : TerminalOutputModelSnapshot {

  override val immutableText: CharSequence
    get() = immutableTextImpl(document)

  override val lineCount: Int
    get() = lineCountImpl(document)

  override val modificationStamp: Long
    get() = modificationStampImpl(document)

  override val startOffset: TerminalOffset
    get() = TerminalOffset.of(trimmedCharsCount)

  override val firstLine: TerminalLineIndex
    get() = TerminalLineIndex.of(trimmedLinesCount)

  override fun getLineByOffset(offset: TerminalOffset): TerminalLineIndex =
    getLineByOffsetImpl(trimmedLinesCount, document, offset.toRelative())

  override fun getStartOfLine(line: TerminalLineIndex): TerminalOffset =
    getStartOfLineImpl(trimmedCharsCount, document, line.toRelative())

  override fun getEndOfLine(line: TerminalLineIndex, includeEOL: Boolean): TerminalOffset =
    getEndOfLineImpl(trimmedCharsCount, document, line.toRelative(), includeEOL)

  override fun getText(start: TerminalOffset, end: TerminalOffset): String =
    getTextImpl(document, start.toRelative(), end.toRelative())

  private fun TerminalOffset.toRelative(): Int = (this - startOffset).toInt()

  private fun TerminalLineIndex.toRelative(): Int = (this - firstLine).toInt()
}

private fun immutableTextImpl(document: Document): CharSequence = document.immutableCharSequence

private fun lineCountImpl(document: Document): Int = document.lineCount.let { if (it > 0) it else 1 }

private fun modificationStampImpl(document: Document): Long = document.modificationStamp

private fun getLineByOffsetImpl(trimmedLinesCount: Long, document: Document, relativeOffset: Int): TerminalLineIndex =
  TerminalLineIndex.of(trimmedLinesCount + document.getLineNumber(relativeOffset))

private fun getStartOfLineImpl(trimmedCharsCount: Long, document: Document, relativeLine: Int): TerminalOffset =
  TerminalOffset.of(trimmedCharsCount + document.getLineStartOffset(relativeLine))

private fun getEndOfLineImpl(
  trimmedCharsCount: Long,
  document: Document,
  relativeLine: Int,
  includeEOL: Boolean,
): TerminalOffset {
  var result = document.getLineEndOffset(relativeLine)
  if (includeEOL && result < document.textLength) {
    ++result
  }
  return TerminalOffset.of(trimmedCharsCount + result)
}

private fun getTextImpl(
  document: Document,
  relativeStart: Int,
  relativeEnd: Int,
): String = document.getText(TextRange(relativeStart, relativeEnd))

private data class TerminalContentChangedImpl(
  override val model: MutableTerminalOutputModelImpl,
  override val offset: TerminalOffset,
  override val oldText: CharSequence,
  override val newText: CharSequence,
  override val isTypeAhead: Boolean,
  override val isTrimming: Boolean,
) : TerminalContentChanged

private data class TerminalCursorOffsetChangedImpl(
  override val model: MutableTerminalOutputModelImpl,
  override val oldOffset: TerminalOffset,
  override val newOffset: TerminalOffset,
) : TerminalCursorOffsetChanged

private val LOG = logger<MutableTerminalOutputModelImpl>()
