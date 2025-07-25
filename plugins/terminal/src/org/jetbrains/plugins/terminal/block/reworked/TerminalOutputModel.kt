// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.FrozenDocument
import com.intellij.terminal.session.StyleRange
import com.intellij.terminal.session.TerminalContentUpdatedEvent
import com.intellij.terminal.session.TerminalOutputModelState
import com.intellij.terminal.session.dto.toStyleRange
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.output.HighlightingInfo
import org.jetbrains.plugins.terminal.block.output.TerminalOutputHighlightingsSnapshot

/**
 * Model that should manage the terminal output content: text, highlightings, and cursor position.
 */
@ApiStatus.Internal
interface TerminalOutputModel {
  val document: Document

  /**
   * Offset in the document where the cursor is located now.
   */
  val cursorOffsetState: StateFlow<Int>

  fun freeze(): FrozenTerminalOutputModel

  fun getAbsoluteLineIndex(documentOffset: Int): Long
  
  fun relativeOffset(offset: Int): TerminalOffset

  fun absoluteOffset(offset: Long): TerminalOffset

  /**
   * Returns document ranges with corresponding text attributes.
   */
  fun getHighlightings(): TerminalOutputHighlightingsSnapshot

  /** Returns null if there is no specific highlighting range at [documentOffset] */
  fun getHighlightingAt(documentOffset: Int): HighlightingInfo?

  /**
   * Executes the given block with the model in the type-ahead mode.
   *
   * In this mode, document changes are reported with to [TerminalOutputModelListener.afterContentChanged]
   * with `isTypeAhead == true`.
   */
  fun withTypeAhead(block: () -> Unit)

  /**
   * [absoluteLineIndex] is the index of the line from the start of the terminal output.
   */
  fun updateContent(absoluteLineIndex: Long, text: String, styles: List<StyleRange>)

  fun replaceContent(offset: TerminalOffset, length: Int, text: String, newStyles: List<StyleRange>)

  /**
   * [absoluteLineIndex] is the index of the line from the start of the terminal output.
   */
  fun updateCursorPosition(absoluteLineIndex: Long, columnIndex: Int)
  
  fun updateCursorPosition(offset: TerminalOffset)

  fun addListener(parentDisposable: Disposable, listener: TerminalOutputModelListener)

  fun dumpState(): TerminalOutputModelState

  fun restoreFromState(state: TerminalOutputModelState)

  companion object {
    val KEY: DataKey<TerminalOutputModel> = DataKey.create("TerminalOutputModel")
  }
}

@ApiStatus.Internal
interface FrozenTerminalOutputModel {
  val document: FrozenDocument

  val cursorOffset: Int

  fun relativeOffset(offset: Int): TerminalOffset
  fun absoluteOffset(offset: Long): TerminalOffset
}

@ApiStatus.Internal
sealed interface TerminalOffset : Comparable<TerminalOffset> {
  fun toAbsolute(): Long
  fun toRelative(): Int
}

@ApiStatus.Internal
fun TerminalOutputModel.updateContent(event: TerminalContentUpdatedEvent) {
  val styles = event.styles.map { it.toStyleRange() }
  updateContent(event.startLineLogicalIndex, event.text, styles)
}
