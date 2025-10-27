// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.output.HighlightingInfo
import org.jetbrains.plugins.terminal.block.output.TerminalOutputHighlightingsSnapshot

@ApiStatus.Experimental
sealed interface TerminalOutputModel {
  companion object {
    val KEY: Key<TerminalOutputModel> = Key.create("TerminalOutputModel")
    val DATA_KEY: DataKey<TerminalOutputModel> = DataKey.create("TerminalOutputModel")
  }

  val textLength: Int

  val lineCount: Int

  val modificationStamp: Long

  val cursorOffset: TerminalOffset

  val startOffset: TerminalOffset

  val firstLineIndex: TerminalLineIndex

  fun addListener(parentDisposable: Disposable, listener: TerminalOutputModelListener)

  fun takeSnapshot(): TerminalOutputModelSnapshot

  fun getLineByOffset(offset: TerminalOffset): TerminalLineIndex

  fun getStartOfLine(line: TerminalLineIndex): TerminalOffset

  fun getEndOfLine(line: TerminalLineIndex, includeEOL: Boolean = false): TerminalOffset

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

@ApiStatus.Experimental
sealed interface TerminalOutputModelSnapshot {
  val textLength: Int

  val lineCount: Int

  val modificationStamp: Long

  val cursorOffset: TerminalOffset

  val startOffset: TerminalOffset

  val firstLineIndex: TerminalLineIndex

  fun getLineByOffset(offset: TerminalOffset): TerminalLineIndex

  fun getStartOfLine(line: TerminalLineIndex): TerminalOffset

  fun getEndOfLine(line: TerminalLineIndex, includeEOL: Boolean = false): TerminalOffset

  fun getText(start: TerminalOffset, end: TerminalOffset): CharSequence
}

@ApiStatus.Experimental
sealed interface TerminalOffset : Comparable<TerminalOffset> {
  companion object {
    @JvmStatic fun of(absoluteOffset: Long): TerminalOffset = TerminalOffsetImpl(absoluteOffset)
    @JvmField val ZERO: TerminalOffset = of(0L)
  }
  fun toAbsolute(): Long
  operator fun plus(charCount: Long): TerminalOffset
  operator fun minus(charCount: Long): TerminalOffset
  operator fun minus(other: TerminalOffset): Long
}

@ApiStatus.Experimental
sealed interface TerminalLineIndex : Comparable<TerminalLineIndex> {
  companion object {
    @JvmStatic fun of(absoluteOffset: Long): TerminalLineIndex = TerminalLineIndexImpl(absoluteOffset)
    @JvmField val ZERO: TerminalLineIndex = of(0L)
  }
  fun toAbsolute(): Long
  operator fun plus(lineCount: Long): TerminalLineIndex
  operator fun minus(lineCount: Long): TerminalLineIndex
  operator fun minus(other: TerminalLineIndex): Long
}

@get:ApiStatus.Experimental
val TerminalOutputModel.endOffset: TerminalOffset
  get() = startOffset + textLength.toLong()

@get:ApiStatus.Experimental
val TerminalOutputModel.lastLineIndex: TerminalLineIndex
  get() = firstLineIndex + (lineCount - 1).toLong()

@get:ApiStatus.Experimental
val TerminalOutputModelSnapshot.endOffset: TerminalOffset
  get() = startOffset + textLength.toLong()

@get:ApiStatus.Experimental
val TerminalOutputModelSnapshot.lastLineIndex: TerminalLineIndex
  get() = firstLineIndex + (lineCount - 1).toLong()


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
