// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.terminal.block.output.TerminalOutputHighlightingsSnapshot
import org.jetbrains.plugins.terminal.block.session.StyleRange

/**
 * Model that should manage the terminal output content: text, highlightings, and cursor position.
 */
internal interface TerminalOutputModel {
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
  fun updateContent(absoluteLineIndex: Int, text: String, styles: List<StyleRange>)

  /**
   * [absoluteLineIndex] is the index of the line from the start of the terminal output.
   */
  fun updateCursorPosition(absoluteLineIndex: Int, columnIndex: Int)

  fun addListener(parentDisposable: Disposable, listener: TerminalOutputModelListener)
}