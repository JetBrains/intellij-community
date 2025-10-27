// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.impl

import com.intellij.openapi.editor.Document
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.session.impl.StyleRange
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputModelState
import org.jetbrains.plugins.terminal.session.impl.dto.toStyleRange
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel

/**
 * Model that should manage the terminal output content: text, highlightings, and cursor position.
 */
@ApiStatus.Internal
sealed interface MutableTerminalOutputModel : TerminalOutputModel {
  val document: Document

  /**
   * Executes the given block with the model in the type-ahead mode.
   *
   * In this mode, document changes are reported with to [org.jetbrains.plugins.terminal.view.TerminalOutputModelListener.afterContentChanged]
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

  fun dumpState(): TerminalOutputModelState

  fun restoreFromState(state: TerminalOutputModelState)
}

@ApiStatus.Internal
fun MutableTerminalOutputModel.updateContent(event: TerminalContentUpdatedEvent) {
  val styles = event.styles.map { it.toStyleRange() }
  updateContent(event.startLineLogicalIndex, event.text, styles)
}
