// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.Document
import com.intellij.terminal.session.StyleRange
import com.intellij.terminal.session.TerminalOutputModelState
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
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

  /**
   * Returns document ranges with corresponding text attributes.
   */
  fun getHighlightings(): TerminalOutputHighlightingsSnapshot

  /**
   * [absoluteLineIndex] is the index of the line from the start of the terminal output.
   */
  fun updateContent(absoluteLineIndex: Long, text: String, styles: List<StyleRange>)

  /**
   * [absoluteLineIndex] is the index of the line from the start of the terminal output.
   */
  fun updateCursorPosition(absoluteLineIndex: Long, columnIndex: Int)

  fun addListener(parentDisposable: Disposable, listener: TerminalOutputModelListener)

  fun dumpState(): TerminalOutputModelState

  fun restoreFromState(state: TerminalOutputModelState)

  companion object {
    val KEY: DataKey<TerminalOutputModel> = DataKey.create("TerminalOutputModel")
  }
}