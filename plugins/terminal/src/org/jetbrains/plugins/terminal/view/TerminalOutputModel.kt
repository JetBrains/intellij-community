// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.output.HighlightingInfo
import org.jetbrains.plugins.terminal.block.output.TerminalOutputHighlightingsSnapshot
import org.jetbrains.plugins.terminal.view.TerminalOutputModel.Companion.DATA_KEY

/**
 * A read-only view of the contents of the terminal screen and history buffer.
 *
 * An output model can be thought of as a text string that contains the currently displayed text
 * and some previous history, that is removed ("trimmed") from time to time to avoid consuming too much memory.
 * The model keeps track of how many characters were trimmed and provides "absolute" offsets
 * (from the very beginning). The currently available "window" has the length [textLength] and is located between [startOffset] and [endOffset] (exclusive).
 *
 * In addition to offset-based indexing, logical line-based indexing is also available.
 * A logical line in this context is a line of process output that may correspond to several visual lines in the terminal
 * in the case when it was wrapped because it was too long to fit into the terminal.
 * Available logical line indexes are between [firstLineIndex] and [lastLineIndex] (inclusive).
 *
 * Normally trimming works in such a way that [startOffset] keeps increasing steadily, reflecting the removal of text chunks at the start.
 * However, there can be exceptions to this behavior. For example, when the whole buffer is cleared, the offsets may be reset to zero.
 * In remote development scenarios, when the frontend reconnects to the backend and the state is reset
 * to whatever state the backend model had at the moment when the frontend connected.
 *
 * The interface provides a read-only view, but the model itself is mutable and therefore should only be accessed on the mutating thread,
 * which is currently the EDT. Model listeners (see [addListener]) are synchronous and therefore the model can be safely accessed from listeners.
 * If background access is needed (for example, for some CPU-intensive processing), it's possible to take a snapshot using [takeSnapshot].
 *
 * In most environments, a running terminal session provides two models: one for the main buffer and one for the alternate buffer
 * (used to run "fullscreen" apps like Vim). The currently active model may be retrieved from the data context using the [DATA_KEY] key.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalOutputModel {
  companion object {
    val KEY: Key<TerminalOutputModel> = Key.create("TerminalOutputModel")

    /**
     * The data context key for accessing the currently active model.
     *
     * @see [com.intellij.openapi.actionSystem.DataContext]
     */
    val DATA_KEY: DataKey<TerminalOutputModel> = DataKey.create("TerminalOutputModel")
  }

  /**
   * The length of the currently available text.
   */
  val textLength: Int

  /**
   * The line count in the currently available text.
   *
   * Always positive. An empty model is considered consisting from a single empty line.
   *
   * @see firstLineIndex
   * @see lastLineIndex
   */
  val lineCount: Int

  /**
   * A counter reflecting the number of modifications to the model.
   *
   * Not related to modification time in any way. Incremented every time any change is made.
   */
  val modificationStamp: Long

  /**
   * The current offset of the terminal cursor.
   *
   * Guaranteed to always be between [startOffset] and [endOffset] (inclusive).
   */
  val cursorOffset: TerminalOffset

  /**
   * The current offset of the start of the available part of the output.
   *
   * Anything before this index has been already trimmed and no longer available.
   */
  val startOffset: TerminalOffset

  /**
   * The current offset of the end of the available part of the output.
   */
  val endOffset: TerminalOffset
    get() = startOffset + textLength.toLong()

  /**
   * The index of the first line in the available part of the output.
   *
   * Anything before this line has been already trimmed and no longer available.
   * The first line itself may be partially trimmed as well.
   */
  val firstLineIndex: TerminalLineIndex

  /**
   * The index of the last line in the available part of the output.
   *
   * Note that unlike offsets this is inclusive. The reason is that there's always at least one valid line, see [lineCount].
   */
  val lastLineIndex: TerminalLineIndex
    get() = firstLineIndex + (lineCount - 1).toLong()

  /**
   * Adds a listener that receives model change events.
   *
   * @param parentDisposable the listener will be removed when this disposable is disposed
   */
  fun addListener(parentDisposable: Disposable, listener: TerminalOutputModelListener)

  /**
   * Takes a snapshot of the current state of the model.
   *
   * Taking a snapshot is a relatively cheap operation, as it doesn't make a full deep copy of the text.
   * Instead, it relies on cheap-to-copy immutable data structures used by the underlying implementation.
   * See [getText] for the trade-offs.
   */
  fun takeSnapshot(): TerminalOutputModelSnapshot

  /**
   * Returns the line index where the given offset is located.
   *
   * The offset must be between [startOffset] and [endOffset] (inclusive), otherwise an exception is thrown.
   */
  fun getLineByOffset(offset: TerminalOffset): TerminalLineIndex

  /**
   * Returns the start offset of the given line.
   *
   * The line index must be between [firstLineIndex] and [lastLineIndex] (inclusive), otherwise an exception is thrown.
   */
  fun getStartOfLine(line: TerminalLineIndex): TerminalOffset

  /**
   * Returns the end offset of the given line.
   *
   * The line index must be between [firstLineIndex] and [lastLineIndex] (inclusive), otherwise an exception is thrown.
   *
   * @param includeEOL if `true`, then the offset beyond the line terminator is returned, unless it's the last line and there's no terminator
   */
  fun getEndOfLine(line: TerminalLineIndex, includeEOL: Boolean = false): TerminalOffset

  /**
   * Returns the text between the given offsets.
   *
   * Both the start and the end offsets must be between [startOffset] and [endOffset] (inclusive),
   * and it must be that `start <= end`. Otherwise, an exception is thrown.
   *
   * This method returns a [CharSequence] and not a [String] because it relies on the underlying implementation data structures
   * that allow making fast copies without deep copying the whole content. The trade-off for this implementation is that the returned
   * text is considerably slower to access than a regular string. For this reason, in any performance-sensitive code it's recommended
   * to make a deep copy first by calling [toString] and then iterate over the returned copy.
   */
  fun getText(start: TerminalOffset, end: TerminalOffset): CharSequence

  /**
   * Returns document ranges with corresponding text attributes.
   */
  @ApiStatus.Internal
  fun getHighlightings(): TerminalOutputHighlightingsSnapshot

  /** Returns null if there is no specific highlighting range at [documentOffset] */
  @ApiStatus.Internal
  fun getHighlightingAt(documentOffset: TerminalOffset): HighlightingInfo?
}

/**
 * A snapshot of [TerminalOutputModel].
 *
 * @see [TerminalOutputModel.takeSnapshot]
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalOutputModelSnapshot {
  /**
   * The length of the text available in the snapshot.
   */
  val textLength: Int

  /**
   * The number of lines available in the snapshot.
   *
   * Always positive, see [TerminalOutputModel.lineCount].
   */
  val lineCount: Int

  /**
   * The modification stamp the model had at the moment the snapshot was taken.
   *
   * @see [TerminalOutputModel.modificationStamp]
   */
  val modificationStamp: Long

  /**
   * The cursor offset the moment the snapshot was taken.
   */
  val cursorOffset: TerminalOffset

  /**
   * The start offset of the text available in the snapshot.
   */
  val startOffset: TerminalOffset

  /**
   * The offset of the end of the available part of the output the moment the snapshot was taken.
   *
   * Anything before this offset has been already trimmed and no longer available.
   */
  val endOffset: TerminalOffset
    get() = startOffset + textLength.toLong()

  /**
   * The index of the first line available in the snapshot.
   */
  val firstLineIndex: TerminalLineIndex

  /**
   * The index of the last line in the available part of the output the moment the snapshot was taken.
   *
   * Note that unlike offsets this is inclusive. The reason is that there's always at least one valid line,
   * see [lineCount].
   */
  val lastLineIndex: TerminalLineIndex
    get() = firstLineIndex + (lineCount - 1).toLong()

  /**
   * Returns the line index that contains the given offset.
   *
   * @see [TerminalOutputModel.getLineByOffset]
   */
  fun getLineByOffset(offset: TerminalOffset): TerminalLineIndex

  /**
   * Returns the start offset of the given line.
   *
   * @see [TerminalOutputModel.getStartOfLine]
   */
  fun getStartOfLine(line: TerminalLineIndex): TerminalOffset

  /**
   * Returns the end offset of the given line.
   *
   * @see [TerminalOutputModel.getEndOfLine]
   */
  fun getEndOfLine(line: TerminalLineIndex, includeEOL: Boolean = false): TerminalOffset

  /**
   * Returns the text in the given range.
   *
   * The same restrictions and trade-offs apply as for [TerminalOutputModel.getText].
   */
  fun getText(start: TerminalOffset, end: TerminalOffset): CharSequence
}

/**
 * An offset in the terminal output.
 *
 * Offsets are absolute, see [TerminalOutputModel] for details.
 *
 * Offsets are allowed to have any values. Whether an offset is valid or not, depends on the context.
 * In most cases valid offsets are nonnegative, and often are required to be between
 * [TerminalOutputModel.startOffset] and [TerminalOutputModel.endOffset] (inclusive or exclusive).
 */
@ApiStatus.Experimental
sealed interface TerminalOffset : Comparable<TerminalOffset> {
  companion object {
    /**
     * Creates a new offset instance with the given value.
     */
    @JvmStatic fun of(absoluteOffset: Long): TerminalOffset = TerminalOffsetImpl(absoluteOffset)

    /**
     * The offset of the beginning of the output history.
     */
    @JvmField val ZERO: TerminalOffset = of(0L)
  }

  /**
   * Returns the absolute offset from the beginning of the output history.
   */
  fun toAbsolute(): Long

  /**
   * Adds the given value to the offset and returns the new offset.
   */
  operator fun plus(charCount: Long): TerminalOffset

  /**
   * Subtracts the given value from the offset and returns the new offset.
   */
  operator fun minus(charCount: Long): TerminalOffset

  /**
   * Calculates the difference between this offset and the other offset.
   */
  operator fun minus(other: TerminalOffset): Long
}

/**
 * A line index in the terminal output.
 *
 * Line indices are absolute, see [TerminalOutputModel] for details.
 *
 * Line indices are allowed to have any values. Whether a line index is valid or not, depends on the context.
 * In most cases valid offsets are nonnegative, and often are required to be between
 * [TerminalOutputModel.firstLineIndex] and [TerminalOutputModel.lastLineIndex] (inclusive).
 */
@ApiStatus.Experimental
sealed interface TerminalLineIndex : Comparable<TerminalLineIndex> {
  companion object {
    /**
     * Creates a new line index instance with the given value.
     */
    @JvmStatic fun of(absoluteOffset: Long): TerminalLineIndex = TerminalLineIndexImpl(absoluteOffset)

    /**
     * The line index of the beginning of the output history.
     */
    @JvmField val ZERO: TerminalLineIndex = of(0L)
  }

  /**
   * Returns the absolute line index from the beginning of the output history.
   */
  fun toAbsolute(): Long

  /**
   * Adds the given value to the line index and returns the new line index.
   */
  operator fun plus(lineCount: Long): TerminalLineIndex

  /**
   * Subtracts the given value from the line index and returns the new line index.
   */
  operator fun minus(lineCount: Long): TerminalLineIndex

  /**
   * Calculates the difference between this line index and the other line index.
   */
  operator fun minus(other: TerminalLineIndex): Long
}


private data class TerminalOffsetImpl(private val absolute: Long) : TerminalOffset {
  override fun compareTo(other: TerminalOffset): Int = toAbsolute().compareTo(other.toAbsolute())
  override fun toAbsolute(): Long = absolute
  override fun plus(charCount: Long): TerminalOffset = TerminalOffsetImpl(absolute + charCount)
  override fun minus(charCount: Long): TerminalOffset = plus(-charCount)
  override fun minus(other: TerminalOffset): Long = toAbsolute() - other.toAbsolute()
  override fun toString(): String = "${toAbsolute()}L"
}

private data class TerminalLineIndexImpl(private val absolute: Long) : TerminalLineIndex {
  override fun compareTo(other: TerminalLineIndex): Int = toAbsolute().compareTo(other.toAbsolute())
  override fun toAbsolute(): Long = absolute
  override fun plus(lineCount: Long): TerminalLineIndex = TerminalLineIndexImpl(absolute + lineCount)
  override fun minus(lineCount: Long): TerminalLineIndex = TerminalLineIndexImpl(absolute - lineCount)
  override fun minus(other: TerminalLineIndex): Long = toAbsolute() - other.toAbsolute()
  override fun toString(): String = "${toAbsolute()}L"
}
