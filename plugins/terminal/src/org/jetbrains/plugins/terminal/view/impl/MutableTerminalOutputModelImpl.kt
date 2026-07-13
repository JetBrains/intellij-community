// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.FrozenDocument
import com.intellij.terminal.TerminalColorPalette
import com.intellij.util.containers.DisposableWrapperList
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.block.output.HighlightingInfo
import org.jetbrains.plugins.terminal.block.output.TerminalOutputHighlightingsSnapshot
import org.jetbrains.plugins.terminal.block.output.TextStyleAdapter
import org.jetbrains.plugins.terminal.block.ui.BlockTerminalColorPalette
import org.jetbrains.plugins.terminal.session.impl.Osc8Hyperlink
import org.jetbrains.plugins.terminal.session.impl.StyleRange
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputModelState
import org.jetbrains.plugins.terminal.util.fireListenersAndLogAllExceptions
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalCursorOffsetChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.view.TerminalOutputModelSnapshot
import org.jetbrains.plugins.terminal.view.TerminalOutputOsc8Hyperlink

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

  private val osc8HyperlinksModel = Osc8HyperlinksModel()

  private val listeners = DisposableWrapperList<TerminalOutputModelListener>()

  @VisibleForTesting
  var trimmedLinesCount: Long = 0

  @VisibleForTesting
  var trimmedCharsCount: Long = 0

  @VisibleForTesting
  var firstLineTrimmedCharsCount: Int = 0

  private var contentUpdateInProgress: Boolean = false

  private var isTypeAhead: Boolean = false

  override val textLength: Int
    get() = document.textLength

  override val lineCount: Int
    get() = lineCountImpl(document)

  override val modificationStamp: Long
    get() = modificationStampImpl(document)

  override val startOffset: TerminalOffset
    get() = TerminalOffset.of(trimmedCharsCount)

  override val firstLineIndex: TerminalLineIndex
    get() = TerminalLineIndex.of(trimmedLinesCount)

  private fun relativeOffset(offset: Int): TerminalOffset =
    TerminalOffset.of(trimmedCharsCount + offset)

  override fun getLineByOffset(offset: TerminalOffset): TerminalLineIndex =
    getLineByOffsetImpl(trimmedLinesCount, document, offset.toRelative())

  override fun getStartOfLine(line: TerminalLineIndex): TerminalOffset =
    getStartOfLineImpl(trimmedCharsCount, document, line.toRelative())

  override fun getEndOfLine(line: TerminalLineIndex, includeEOL: Boolean): TerminalOffset =
    getEndOfLineImpl(trimmedCharsCount, document, line.toRelative(), includeEOL)

  override fun getText(start: TerminalOffset, end: TerminalOffset): CharSequence =
    getTextImpl(document, start.toRelative(), end.toRelative())

  private fun TerminalOffset.toRelative(): Int = (this - startOffset).toInt()

  private fun TerminalLineIndex.toRelative(): Int = (this - firstLineIndex).toInt()

  override fun takeSnapshot(): TerminalOutputModelSnapshot =
    TerminalOutputModelSnapshotImpl((document as DocumentImpl).freeze(), trimmedCharsCount, trimmedLinesCount, cursorOffset)

  override fun updateContent(
    absoluteLineIndex: Long,
    text: String,
    styles: List<StyleRange>,
    osc8Hyperlinks: List<Osc8Hyperlink>,
  ) {
    // If absolute line index is far in the past - in the already trimmed part of the output,
    // then it means that the terminal was cleared, and we should reset to the initial state.
    if (absoluteLineIndex < trimmedLinesCount) {
      changeDocumentContent {
        clear()
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
      doReplaceContentIgnoringEqualPrefixAndOrSuffix(startOffset, (endOffset - startOffset).toInt(), text, styles, osc8Hyperlinks)
    }
  }

  override fun replaceContent(offset: TerminalOffset, length: Int, text: String, newStyles: List<StyleRange>) {
    changeDocumentContent(isTypeAhead) { 
      doReplaceContentIgnoringEqualPrefixAndOrSuffix(offset, length, text, newStyles, emptyList())
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
    val trimmedCharsInLine = if (lineIndex == firstLineIndex) firstLineTrimmedCharsCount else 0
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
        doReplaceContentIgnoringEqualPrefixAndOrSuffix(lineEndOffset, 0, spaces, emptyList(), emptyList())
      }
    }

    val newCursorOffset = lineStartOffset + trimmedColumnIndex.toLong()
    LOG.debug { "Updated the cursor position to $newCursorOffset" }
    updateCursorPosition(newCursorOffset)
  }

  override fun updateCursorPosition(offset: TerminalOffset) {
    if (cursorOffset == offset) return
    val oldValue = cursorOffset
    this.cursorOffset = offset

    val event = TerminalCursorOffsetChangeEventImpl(this, oldValue, offset)
    fireListenersAndLogAllExceptions(listeners, LOG, "Exception during handling $event") {
      it.cursorOffsetChanged(event)
    }
  }

  private fun ensureDocumentHasLine(lineIndex: TerminalLineIndex) {
    if (lineIndex > lastLineIndex) {
      changeDocumentContent {
        val newLinesToAdd = (lineIndex - lastLineIndex).toInt()
        val newLines = "\n".repeat(newLinesToAdd)
        LOG.debug { "Add $newLinesToAdd lines to make the line valid" }
        doReplaceContentIgnoringEqualPrefixAndOrSuffix(endOffset, 0, newLines, emptyList(), emptyList())
      }
    }
  }
  
  private fun clear(): ModelChange {
    val oldText = getText(startOffset, endOffset)
    trimmedLinesCount = 0
    trimmedCharsCount = 0
    firstLineTrimmedCharsCount = 0
    document.replaceString(0, textLength, "")
    highlightingsModel.clear()
    osc8HyperlinksModel.clear()
    // Report ZERO and not the actual old offset because listeners expect the offset to be consistent with the model.
    return ModelChange(TerminalOffset.ZERO, oldText, "")
  }

  private fun doReplaceContentIgnoringEqualPrefixAndOrSuffix(
    startOffset: TerminalOffset,
    length: Int,
    newText: String,
    styles: List<StyleRange>,
    osc8Hyperlinks: List<Osc8Hyperlink>,
  ): ModelChange {
    val endOffset = startOffset + length.toLong()
    val effectiveStartOffset = findFirstChangeOffset(startOffset, length, newText)
    val skipPrefix = (effectiveStartOffset - startOffset).toInt()
    val effectiveEndOffset = findLastChangeOffset(effectiveStartOffset, length - skipPrefix, newText, skipPrefix) + 1 // exclusive
    val skipSuffix = (endOffset - effectiveEndOffset).toInt()
    val effectiveLength = length - skipPrefix - skipSuffix
    val effectiveNewText = newText.subSequence(skipPrefix, newText.length - skipSuffix)
    val change = doReplaceContent(effectiveStartOffset, effectiveLength, effectiveNewText)
    highlightingsModel.updateHighlightings(startOffset.toRelative(), length, newText.length, styles)
    osc8HyperlinksModel.update(startOffset.toRelative(), length, newText.length, osc8Hyperlinks)
    return change
  }

  private fun doReplaceContent(startOffset: TerminalOffset, length: Int, newText: CharSequence): ModelChange {
    val relativeStartOffset = startOffset.toRelative()
    val relativeEndOffset = relativeStartOffset + length
    val oldText = document.immutableCharSequence.subSequence(relativeStartOffset, relativeEndOffset)
    document.replaceString(relativeStartOffset, relativeEndOffset, newText)
    return ModelChange(
      startOffset,
      oldText,
      newText
    )
  }

  private fun findFirstChangeOffset(startOffset: TerminalOffset, length: Int, newText: String): TerminalOffset {
    val relativeStartOffset = startOffset.toRelative()
    var srcIndex = relativeStartOffset
    var dstIndex = 0
    val text = document.immutableCharSequence
    while (srcIndex < relativeStartOffset + length && dstIndex < newText.length && text[srcIndex] == newText[dstIndex]) {
      ++srcIndex
      ++dstIndex
    }
    return relativeOffset(srcIndex)
  }

  private fun findLastChangeOffset(startOffset: TerminalOffset, length: Int, newText: String, newTextStart: Int): TerminalOffset {
    val relativeStartOffset = startOffset.toRelative()
    var srcIndex = relativeStartOffset + length - 1
    var dstIndex = newText.length - 1
    val text = document.immutableCharSequence
    while (srcIndex >= relativeStartOffset && dstIndex >= newTextStart && text[srcIndex] == newText[dstIndex]) {
      --srcIndex
      --dstIndex
    }
    return relativeOffset(srcIndex)
  }

  private fun ensureCorrectCursorOffset() {
    // If the document became shorter or was trimmed, immediately ensure that the cursor is still within the document.
    // It'll update itself later to the correct position anyway, but having the incorrect value can cause exceptions before that.
    if (cursorOffset < startOffset) {
      updateCursorPosition(startOffset)
    }
    if (cursorOffset > endOffset) {
      updateCursorPosition(endOffset)
    }
  }

  private fun isTrimNeeded(): Boolean = maxOutputLength > 0 && document.textLength > maxOutputLength

  /** Returns trimmed characters count */
  private fun trimToSize(): CharSequence {
    check(maxOutputLength > 0) { "trimToSize should only be called if trimming is enabled" }
    val textLength = document.textLength
    check(textLength > maxOutputLength) { "This method should be called only if text length $textLength is greater than max length $maxOutputLength" }

    val lineCountBefore = document.lineCount
    val removeUntilOffset = textLength - maxOutputLength
    val futureFirstLineNumber = document.getLineNumber(removeUntilOffset)
    val futureFirstLineStart = document.getLineStartOffset(futureFirstLineNumber)

    highlightingsModel.removeBefore(removeUntilOffset)
    osc8HyperlinksModel.removeBefore(removeUntilOffset)

    val trimmedPart = document.immutableCharSequence.subSequence(0, removeUntilOffset)
    document.deleteString(0, removeUntilOffset)

    trimmedCharsCount += removeUntilOffset
    trimmedLinesCount += lineCountBefore - document.lineCount
    firstLineTrimmedCharsCount = removeUntilOffset - futureFirstLineStart

    return trimmedPart
  }

  /**
   * Document changes in this model are allowed only inside [block] of this function.
   * [block] should return an offset from which document content was changed.
   */
  private fun changeDocumentContent(isTypeAhead: Boolean = false, block: () -> ModelChange) {
    val changeEvent = doSingleDocumentChange(isTypeAhead) {
      val change = block()
      TerminalContentChangeEventImpl(
        this,
        change.offset,
        change.oldText,
        change.newText,
        isTypeAhead,
        false
      )
    }

    var trimmed = 0
    if (isTrimNeeded()) {
      val trimEvent = doSingleDocumentChange(isTypeAhead) {
        val startBeforeTrimming = startOffset
        val trimmedSequence = trimToSize()
        TerminalContentChangeEventImpl(
          this,
          startBeforeTrimming,
          trimmedSequence,
          "",
          isTypeAhead,
          true
        )
      }
      trimmed = trimEvent.oldText.length
    }

    val startOffset = changeEvent.offset
    val effectiveStartOffset = startOffset.coerceAtLeast(startOffset)
    LOG.debug {
      "Content updated from offset = $startOffset (effectively $effectiveStartOffset), " +
      "new length = ${document.textLength} chars, ${document.lineCount} lines, " +
      "currently trimmed = $trimmedCharsCount (+$trimmed) chars, $trimmedLinesCount lines"
    }
  }

  private inline fun doSingleDocumentChange(isTypeAhead: Boolean, block: () -> TerminalContentChangeEventImpl): TerminalContentChangeEventImpl {
    fireListenersAndLogAllExceptions(listeners, LOG, "Exception during handling beforeContentChanged event") {
      it.beforeContentChanged(this)
    }

    check(!contentUpdateInProgress) { "Recursive content updates aren't supported, schedule an update in a separate event if needed" }
    contentUpdateInProgress = true
    val event = try {
      block()
    }
    finally {
      contentUpdateInProgress = false
    }
    ensureCorrectCursorOffset()

    fireListenersAndLogAllExceptions(listeners, LOG, "Exception during handling $event") {
      it.afterContentChanged(event)
    }
    return event
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

  override fun getOsc8Hyperlinks(): List<TerminalOutputOsc8Hyperlink> {
    // Like getHighlightings, links may be out of sync with the document while an update is in progress.
    return if (contentUpdateInProgress) emptyList() else osc8HyperlinksModel.getHyperlinks()
  }

  override fun addListener(parentDisposable: Disposable, listener: TerminalOutputModelListener) {
    listeners.add(listener, parentDisposable)
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
      highlightings = highlightingsModel.dumpState(),
      osc8Hyperlinks = osc8HyperlinksModel.dumpState(),
    )
  }

  override fun restoreFromState(state: TerminalOutputModelState) {
    changeDocumentContent {
      val oldText = document.immutableCharSequence
      trimmedLinesCount = state.trimmedLinesCount
      trimmedCharsCount = state.trimmedCharsCount
      firstLineTrimmedCharsCount = state.firstLineTrimmedCharsCount
      document.setText(state.text)
      highlightingsModel.restoreFromState(state.highlightings)
      osc8HyperlinksModel.restoreFromState(state.osc8Hyperlinks)
      updateCursorPosition(relativeOffset(state.cursorOffset))

      ModelChange(startOffset, oldText, state.text)  // the document is changed from right from the start
    }
  }

  private inner class HighlightingsModel {
    private val colorPalette: TerminalColorPalette = BlockTerminalColorPalette()

    /**
     * Contains sorted ranges of the text that are highlighted differently than default.
     * Offsets of the ranges are absolute to support trimming the start of the list
     * without reassigning offsets for the remaining ranges: [removeBefore].
     */
    private val styleRanges = AbsoluteOffsetRanges<StyleRange>(
      startOffsetOf = { it.startOffset },
      endOffsetOf = { it.endOffset },
      withOffsets = { range, start, end -> range.copy(startOffset = start, endOffset = end) },
    )

    /**
     * Contains sorted ranges of the highlightings that cover all document length.
     * Indexes of the ranges are document-relative, so the first range always starts with 0.
     */
    private var highlightingsSnapshot: TerminalOutputHighlightingsSnapshot? = null

    fun getHighlightingsSnapshot(): TerminalOutputHighlightingsSnapshot {
      highlightingsSnapshot?.let {
        return it
      }

      val documentRelativeHighlightings = styleRanges.asIterable().map {
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
      val index = styleRanges.indexOfRangeAt(absoluteOffset)
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
      styleRanges.removeBefore(documentOffset + trimmedCharsCount)
      highlightingsSnapshot = null
    }

    fun clear() {
      styleRanges.clear()
      highlightingsSnapshot = null
    }

    fun updateHighlightings(relativeStartOffset: Int, oldLength: Int, newLength: Int, styles: List<StyleRange>) {
      val absoluteStartOffset = relativeStartOffset + trimmedCharsCount
      val absoluteStyles = styles.map { styleRange ->
        styleRange.copy(
          startOffset = styleRange.startOffset + absoluteStartOffset,
          endOffset = styleRange.endOffset + absoluteStartOffset,
        )
      }
      styleRanges.replace(absoluteStartOffset, oldLength, newLength, absoluteStyles)
      highlightingsSnapshot = null
    }

    fun dumpState(): List<StyleRange> {
      return styleRanges.asIterable().toList()
    }

    fun restoreFromState(state: List<StyleRange>) {
      styleRanges.setAll(state)
      highlightingsSnapshot = null
    }
  }

  private inner class Osc8HyperlinksModel {
    /**
     * Sorted OSC8 hyperlinks with absolute offsets (see [HighlightingsModel.styleRanges]).
     */
    private val hyperlinks = AbsoluteOffsetRanges<Osc8Hyperlink>(
      startOffsetOf = { it.startOffset },
      endOffsetOf = { it.endOffset },
      withOffsets = { link, start, end -> link.copy(startOffset = start, endOffset = end) },
    )

    fun clear() {
      hyperlinks.clear()
    }

    fun removeBefore(documentOffset: Int) {
      hyperlinks.removeBefore(documentOffset + trimmedCharsCount)
    }

    fun update(relativeStartOffset: Int, oldLength: Int, newLength: Int, newHyperlinks: List<Osc8Hyperlink>) {
      val absoluteStartOffset = relativeStartOffset + trimmedCharsCount
      val absoluteHyperlinks = newHyperlinks.map { link ->
        link.copy(
          startOffset = link.startOffset + absoluteStartOffset,
          endOffset = link.endOffset + absoluteStartOffset,
        )
      }
      hyperlinks.replace(absoluteStartOffset, oldLength, newLength, absoluteHyperlinks)
    }

    fun getHyperlinks(): List<TerminalOutputOsc8Hyperlink> = hyperlinks.asIterable().map {
      TerminalOutputOsc8Hyperlink(
        startOffset = TerminalOffset.of(it.startOffset),
        endOffset = TerminalOffset.of(it.endOffset),
        uri = it.uri,
      )
    }

    fun dumpState(): List<Osc8Hyperlink> = hyperlinks.asIterable().toList()

    fun restoreFromState(state: List<Osc8Hyperlink>) {
      hyperlinks.setAll(state)
    }
  }
}

/**
 * A sorted list of non-overlapping ranges keyed by absolute offsets (offsets from the very beginning
 * of the output history, so they stay stable while the start of the output is trimmed).
 *
 * Encapsulates the insert/replace/trim/shift bookkeeping shared by the output-model highlightings and
 * the OSC8 hyperlinks. [T] is the range payload; [startOffsetOf]/[endOffsetOf] read its absolute
 * offsets and [withOffsets] produces a copy of it with new offsets.
 */
private class AbsoluteOffsetRanges<T>(
  private val startOffsetOf: (T) -> Long,
  private val endOffsetOf: (T) -> Long,
  private val withOffsets: (T, Long, Long) -> T,
) {
  private val ranges: MutableList<T> = ArrayDeque() // ArrayDeque is used here for fast removeAt(0).

  fun asIterable(): Iterable<T> = ranges

  operator fun get(index: Int): T = ranges[index]

  fun clear() {
    ranges.clear()
  }

  fun setAll(newRanges: List<T>) {
    ranges.clear()
    ranges.addAll(newRanges)
  }

  /**
   * Removes every range whose start is before [absoluteOffset].
   *
   * A range that straddles [absoluteOffset] (starts before it, ends after it) is removed entirely rather than
   * clipped, so its still-visible suffix loses its decoration. This is intentional and shared by both terminal
   * highlightings and OSC8 hyperlinks: when output-capacity trimming cuts mid-range, that range is dropped for
   * both. If partially visible ranges should ever remain, clip here so both keep behaving the same.
   */
  fun removeBefore(absoluteOffset: Long) {
    val index = ranges.binarySearch { startOffsetOf(it).compareTo(absoluteOffset) }
    val removeUntil = if (index < 0) -index - 1 else index
    repeat(removeUntil) {
      ranges.removeAt(0)
    }
  }

  /** Returns the index of the range covering [absoluteOffset], or a negative value if there is none. */
  fun indexOfRangeAt(absoluteOffset: Long): Int {
    return ranges.binarySearch {
      when {
        endOffsetOf(it) <= absoluteOffset -> -1
        startOffsetOf(it) > absoluteOffset -> 1
        else -> 0
      }
    }
  }

  /**
   * Replaces the ranges within `[absoluteStartOffset, absoluteStartOffset + oldLength)` with
   * [newRanges] (whose offsets are already absolute), shifting the ranges located after the replaced
   * region by `newLength - oldLength`.
   */
  fun replace(absoluteStartOffset: Long, oldLength: Int, newLength: Int, newRanges: List<T>) {
    val absoluteEndOffset = absoluteStartOffset + oldLength
    val lastUnaffectedIndexBefore = ranges.binarySearch { endOffsetOf(it).compareTo(absoluteStartOffset) }.let { i ->
      if (i >= 0) i else -i - 2
    }
    val firstUnaffectedIndexAfter = ranges.binarySearch { startOffsetOf(it).compareTo(absoluteEndOffset) }.let { i ->
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
    val insertionIndex = ranges.binarySearch { startOffsetOf(it).compareTo(absoluteEndOffset + shift) }.let { i ->
      if (i >= 0) i else -i - 1
    }
    ranges.addAll(insertionIndex, newRanges)
  }

  private fun shift(shiftFromIndex: Int, shift: Int) {
    if (shift == 0) return
    for (i in shiftFromIndex until ranges.size) {
      val range = ranges[i]
      ranges[i] = withOffsets(range, startOffsetOf(range) + shift, endOffsetOf(range) + shift)
    }
  }

  private fun updateAffectedRanges(affectedIndexes: IntRange, affectedAbsoluteOffsets: LongRange, shift: Int) {
    if (affectedIndexes.isEmpty()) return // affectedAbsoluteOffsets might be empty in case of an insertion, but we still need to split then
    val absoluteStartOffset = affectedAbsoluteOffsets.first
    val absoluteEndOffset = affectedAbsoluteOffsets.last + 1
    val affectedRanges = ranges.subList(affectedIndexes.first, affectedIndexes.last + 1)
    val updatedRanges = mutableListOf<T>()
    for (range in affectedRanges) {
      val start = startOffsetOf(range)
      val end = endOffsetOf(range)
      when {
        start < absoluteStartOffset && end <= absoluteEndOffset -> {
          // the start of the range is retained, the end is trimmed
          updatedRanges.add(withOffsets(range, start, absoluteStartOffset))
        }
        start in affectedAbsoluteOffsets && end > absoluteEndOffset -> {
          // the start of the range is trimmed, the end is retained, the range is shifted
          updatedRanges.add(withOffsets(range, absoluteEndOffset + shift, end + shift))
        }
        start < absoluteStartOffset && end > absoluteEndOffset -> {
          // the range is split, both parts are trimmed, the right part is also shifted
          updatedRanges.add(withOffsets(range, start, absoluteStartOffset))
          updatedRanges.add(withOffsets(range, absoluteEndOffset + shift, end + shift))
        }
        // else the entire range is inside the removed range and therefore is removed
      }
    }
    affectedRanges.clear()
    affectedRanges.addAll(updatedRanges)
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
  override val textLength: Int
    get() = document.textLength

  override val lineCount: Int
    get() = lineCountImpl(document)

  override val modificationStamp: Long
    get() = modificationStampImpl(document)

  override val startOffset: TerminalOffset
    get() = TerminalOffset.of(trimmedCharsCount)

  override val firstLineIndex: TerminalLineIndex
    get() = TerminalLineIndex.of(trimmedLinesCount)

  override fun getLineByOffset(offset: TerminalOffset): TerminalLineIndex =
    getLineByOffsetImpl(trimmedLinesCount, document, offset.toRelative())

  override fun getStartOfLine(line: TerminalLineIndex): TerminalOffset =
    getStartOfLineImpl(trimmedCharsCount, document, line.toRelative())

  override fun getEndOfLine(line: TerminalLineIndex, includeEOL: Boolean): TerminalOffset =
    getEndOfLineImpl(trimmedCharsCount, document, line.toRelative(), includeEOL)

  override fun getText(start: TerminalOffset, end: TerminalOffset): CharSequence =
    getTextImpl(document, start.toRelative(), end.toRelative())

  private fun TerminalOffset.toRelative(): Int = (this - startOffset).toInt()

  private fun TerminalLineIndex.toRelative(): Int = (this - firstLineIndex).toInt()
}

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
): CharSequence = document.immutableCharSequence.subSequence(relativeStart, relativeEnd)

private data class TerminalContentChangeEventImpl(
  override val model: MutableTerminalOutputModelImpl,
  override val offset: TerminalOffset,
  override val oldText: CharSequence,
  override val newText: CharSequence,
  override val isTypeAhead: Boolean,
  override val isTrimming: Boolean,
) : TerminalContentChangeEvent

private data class TerminalCursorOffsetChangeEventImpl(
  override val model: MutableTerminalOutputModelImpl,
  override val oldOffset: TerminalOffset,
  override val newOffset: TerminalOffset,
) : TerminalCursorOffsetChangeEvent

private val LOG = logger<MutableTerminalOutputModelImpl>()
