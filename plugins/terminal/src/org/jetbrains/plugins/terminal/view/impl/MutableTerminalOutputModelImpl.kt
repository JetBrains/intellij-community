// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.FrozenDocument
import com.intellij.openapi.util.registry.Registry
import com.intellij.terminal.TerminalColorPalette
import com.intellij.util.containers.DisposableWrapperList
import com.intellij.util.diff.Diff
import com.intellij.util.diff.FilesTooBigForDiffException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.block.output.HighlightingInfo
import org.jetbrains.plugins.terminal.block.output.TerminalOutputHighlightingsSnapshot
import org.jetbrains.plugins.terminal.block.output.TextStyleAdapter
import org.jetbrains.plugins.terminal.block.ui.BlockTerminalColorPalette
import org.jetbrains.plugins.terminal.session.impl.StyleRange
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputModelState
import org.jetbrains.plugins.terminal.util.fireListenersAndLogAllExceptions
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalCursorOffsetChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.view.TerminalOutputModelSnapshot

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

  override fun updateContent(absoluteLineIndex: Long, text: String, styles: List<StyleRange>) {
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
      replaceTextAndUpdateStyles(startOffset, (endOffset - startOffset).toInt(), text, styles)
    }
  }

  override fun replaceContent(offset: TerminalOffset, length: Int, text: String, newStyles: List<StyleRange>) {
    changeDocumentContent(isTypeAhead) {
      replaceTextAndUpdateStyles(offset, length, text, newStyles)
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
        replaceTextAndUpdateStyles(lineEndOffset, 0, spaces, emptyList())
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
        replaceTextAndUpdateStyles(endOffset, 0, newLines, emptyList())
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
    // Report ZERO and not the actual old offset because listeners expect the offset to be consistent with the model.
    return ModelChange(TerminalOffset.ZERO, oldText, "")
  }

  private fun replaceTextAndUpdateStyles(startOffset: TerminalOffset, length: Int, newText: String, styles: List<StyleRange>): ModelChange {
    val relativeStartOffset = startOffset.toRelative()
    val oldText = document.immutableCharSequence.subSequence(relativeStartOffset, relativeStartOffset + length)
    doReplaceText(relativeStartOffset, oldText, newText)
    // The reported change covers only the characters that actually differ (ignoring the common prefix and suffix).
    val change = computeChange(startOffset, oldText, newText)
    highlightingsModel.updateHighlightings(startOffset.toRelative(), length, newText.length, styles)
    return change
  }

  /**
   * Rewrites the document range that starts at [relativeStartOffset] and currently holds [oldText] into [newText].
   * Can perform it in two ways:
   * 1. Do a single replace in the document
   * 2. Perform minimal set of edits to invalidate less text ranges in the document
   *
   * The second approach is used only if `terminal.minimal.document.edits` Registry is enabled
   * and change happens in the area of the screen (for now, consider that screen is the last [VISIBLE_SCREEN_LINES]).
   * TODO: Need to add information about screen start position to the output model.
   *
   * The single replace approach is faster, but it has a downside: it may invalidate a big range of the document.
   * It leads to hyperlinks removal and blinking effect when they are applied again.
   *
   * The minimal edits approach tries to make several document edits and preserve as much content as possible,
   * so hyperlinks whose text is not really changed, won't be invalidated.
   * The approach of calculating mininal edits is based on a line diff (using [Diff] utility).
   */
  private fun doReplaceText(relativeStartOffset: Int, oldText: CharSequence, newText: String) {
    LOG.trace {
      "REPLACE relStart=$relativeStartOffset len=${oldText.length} docLen=${document.textLength} newLen=${newText.length} " +
      "oldLines=${oldText.count { it == '\n' } + 1} newLines=${newText.count { it == '\n' } + 1}\n" +
      "  OLD:\n${oldText.dumpLinesForTrace()}  NEW:\n${newText.dumpLinesForTrace()}"
    }

    // Use the minimal edits approach only if it is enabled in Registry
    if (!Registry.`is`("terminal.minimal.document.edits")) {
      document.replaceString(relativeStartOffset, relativeStartOffset + oldText.length, newText)
      return
    }

    // Decide whether more performant single replace can be done (if replaced content is too long).
    if (exceedsScreen(newText) || exceedsScreen(oldText)) {
      LOG.trace { "  SINGLE REPLACE (update exceeds $VISIBLE_SCREEN_LINES lines)" }
      document.replaceString(relativeStartOffset, relativeStartOffset + oldText.length, newText)
      return
    }

    // Both old and new text fit the screen: use minimal edits approach
    val oldLines = oldText.toString().split('\n')
    val newLines = newText.split('\n')
    val changes = try {
      Diff.buildChanges(oldLines.toTypedArray(), newLines.toTypedArray())
    }
    catch (_: FilesTooBigForDiffException) {
      document.replaceString(relativeStartOffset, relativeStartOffset + oldText.length, newText)
      return
    }
    // A `null` change means the texts are identical: nothing to do.
    val hunks = changes?.toList() ?: return

    val oldLineStarts = lineStartOffsets(oldLines)
    val newLineStarts = lineStartOffsets(newLines)
    val oldEnd = oldText.length
    val newEnd = newText.length

    // Each [Diff.Change] hunk deletes `deleted` old lines at `line0` and inserts `inserted` new lines at `line1`.
    // The lines between hunks are identical and left untouched (so their markers survive).
    // Apply hunks bottom-up so offsets of not-yet-applied edits stay valid.
    LOG.trace { "  DIFF: oldLines=${oldLines.size} newLines=${newLines.size} hunks=${hunks.size}" }
    for (hunk in hunks.asReversed()) {
      if (hunk.deleted == hunk.inserted) {
        // A same-size hunk: replace each line on its own,
        // so Document.replaceString's own common prefix/suffix trimming keeps RangeMarkers over the unchanged part of a line.
        LOG.trace { "    edit: replace ${hunk.deleted} line(s) individually [oldLines ${hunk.line0}..<${hunk.line0 + hunk.deleted}]" }
        for (k in hunk.deleted - 1 downTo 0) {
          val oldLine = oldLines[hunk.line0 + k]
          val newLine = newLines[hunk.line1 + k]
          if (oldLine == newLine) continue
          val start = relativeStartOffset + oldLineStarts[hunk.line0 + k]
          document.replaceString(start, start + oldLine.length, newLine)
        }
      }
      else {
        // A hunk that changes the number of lines (insertion/deletion): replace it in one edit.
        // The boundaries sit at the end of the identical line before the hunk and the start of the identical line after it.
        val oldHunkStart = if (hunk.line0 > 0) oldLineStarts[hunk.line0 - 1] + oldLines[hunk.line0 - 1].length else 0
        val oldHunkEnd = if (hunk.line0 + hunk.deleted < oldLines.size) oldLineStarts[hunk.line0 + hunk.deleted] else oldEnd
        val newHunkStart = if (hunk.line1 > 0) newLineStarts[hunk.line1 - 1] + newLines[hunk.line1 - 1].length else 0
        val newHunkEnd = if (hunk.line1 + hunk.inserted < newLines.size) newLineStarts[hunk.line1 + hunk.inserted] else newEnd
        LOG.trace { "    edit: replace old [$oldHunkStart,$oldHunkEnd) (${oldHunkEnd - oldHunkStart} chars) with new [$newHunkStart,$newHunkEnd) (${newHunkEnd - newHunkStart} chars)" }
        document.replaceString(relativeStartOffset + oldHunkStart,
                               relativeStartOffset + oldHunkEnd,
                               newText.subSequence(newHunkStart, newHunkEnd))
      }
    }
  }

  private fun lineStartOffsets(lines: List<CharSequence>): IntArray {
    val starts = IntArray(lines.size + 1)
    for (i in lines.indices) {
      starts[i + 1] = starts[i] + lines[i].length + 1
    }
    return starts
  }

  /**
   * Whether [text] spans more than [VISIBLE_SCREEN_LINES] lines.
   */
  private fun exceedsScreen(text: CharSequence): Boolean {
    var newlines = 0
    for (i in text.indices) {
      if (text[i] == '\n' && ++newlines >= VISIBLE_SCREEN_LINES) return true
    }
    return false
  }

  /** Computes the change to report to listeners: the range that differs, ignoring the common character prefix and suffix. */
  private fun computeChange(startOffset: TerminalOffset, oldText: CharSequence, newText: CharSequence): ModelChange {
    val maxCommon = minOf(oldText.length, newText.length)
    var prefix = 0
    while (prefix < maxCommon && oldText[prefix] == newText[prefix]) prefix++
    var suffix = 0
    while (suffix < maxCommon - prefix && oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]) suffix++
    val oldCore = oldText.subSequence(prefix, oldText.length - suffix)
    val newCore = newText.subSequence(prefix, newText.length - suffix)
    return ModelChange(startOffset + prefix.toLong(), oldCore, newCore)
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
      highlightings = highlightingsModel.dumpState()
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
      updateCursorPosition(relativeOffset(state.cursorOffset))

      ModelChange(startOffset, oldText, state.text)  // the document is changed from right from the start
    }
  }

  companion object {
    @VisibleForTesting
    /**
     * The assumed visible-screen height in logical lines.
     */
    const val VISIBLE_SCREEN_LINES: Int = 100
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
    
    fun clear() {
      styleRanges.clear()
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

      highlightingsSnapshot = null
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

private fun CharSequence.dumpLinesForTrace(maxLines: Int = 200, maxLineLength: Int = 120): String {
  val lines = split("\n")
  return buildString {
    for (i in lines.indices) {
      if (i >= maxLines) {
        append("    … (").append(lines.size - maxLines).append(" more lines)\n")
        break
      }
      append("    [").append(i).append("] '").append(lines[i].take(maxLineLength)).append("'\n")
    }
  }
}
