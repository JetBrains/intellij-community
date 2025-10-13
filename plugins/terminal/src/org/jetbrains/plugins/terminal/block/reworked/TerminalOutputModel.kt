// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.util.Key
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.output.HighlightingInfo
import org.jetbrains.plugins.terminal.block.output.TerminalOutputHighlightingsSnapshot

@ApiStatus.Experimental
sealed interface TerminalOutputModel {
  companion object {
    val KEY: Key<TerminalOutputModel> = Key.create("TerminalOutputModel")
    val DATA_KEY: DataKey<TerminalOutputModel> = DataKey.create("TerminalOutputModel")
  }

  val immutableText: CharSequence

  val lineCount: Int

  val modificationStamp: Long

  val cursorOffset: TerminalOffset

  /**
   * Offset in the document where the cursor is located now.
   */
  val cursorOffsetState: StateFlow<TerminalOffset>

  val startOffset: TerminalOffset

  val firstLine: TerminalLineIndex

  val lastLine: TerminalLineIndex

  fun addListener(parentDisposable: Disposable, listener: TerminalOutputModelListener)

  fun snapshot(): TerminalOutputModelSnapshot

  fun absoluteOffset(offset: Long): TerminalOffset

  fun absoluteLine(line: Long): TerminalLineIndex

  fun lineByOffset(offset: TerminalOffset): TerminalLineIndex

  fun startOffset(line: TerminalLineIndex): TerminalOffset

  fun endOffset(line: TerminalLineIndex, includeEOL: Boolean = false): TerminalOffset

  fun getText(start: TerminalOffset, end: TerminalOffset): String

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
sealed interface TerminalOutputModelSnapshot : TerminalOutputModel

@ApiStatus.Experimental
sealed interface TerminalOffset : Comparable<TerminalOffset> {
  fun toAbsolute(): Long
  operator fun plus(charCount: Long): TerminalOffset
  operator fun minus(charCount: Long): TerminalOffset
  operator fun minus(other: TerminalOffset): Long
}

@ApiStatus.Experimental
sealed interface TerminalLineIndex : Comparable<TerminalLineIndex> {
  fun toAbsolute(): Long
  operator fun plus(lineCount: Long): TerminalLineIndex
  operator fun minus(other: TerminalLineIndex): Long
}

@get:ApiStatus.Experimental
val TerminalOutputModel.textLength: Int
  get() = immutableText.length

@get:ApiStatus.Experimental
val TerminalOutputModel.endOffset: TerminalOffset
  get() = startOffset + textLength.toLong()
