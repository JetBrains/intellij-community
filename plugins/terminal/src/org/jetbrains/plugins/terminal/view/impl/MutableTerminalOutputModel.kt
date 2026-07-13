// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.impl

import com.intellij.openapi.editor.Document
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.session.impl.Osc8Hyperlink
import org.jetbrains.plugins.terminal.session.impl.StyleRange
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputModelState
import org.jetbrains.plugins.terminal.session.impl.dto.toOsc8Hyperlink
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
   * Replaces everything from the start of [absoluteLineIndex] (a line index counted from the start of the
   * terminal output) with [text]. The OSC8 hyperlinks over that range are replaced with [osc8Hyperlinks]
   * (hyperlinks overlapping the range keep their parts outside it).
   *
   * [absoluteLineIndex] is the index of the line from the start of the terminal output.
   *
   * [osc8Hyperlinks] are the OSC8 hyperlinks found in [text]; their offsets are relative to [text].
   */
  fun updateContent(
    absoluteLineIndex: Long,
    text: String,
    styles: List<StyleRange> = emptyList(),
    osc8Hyperlinks: List<Osc8Hyperlink> = emptyList(),
  )

  /**
   * Replaces [length] characters at [offset] with [text]. The OSC8 hyperlinks over the
   * range are cleared (hyperlinks overlapping the range keep their parts outside it).
   */
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
  val osc8Hyperlinks = event.osc8Hyperlinks.map { it.toOsc8Hyperlink() }
  updateContent(event.startLineLogicalIndex, event.text, styles, osc8Hyperlinks)
  updateCursorPosition(event.cursorLogicalLineIndex, event.cursorColumnIndex)
}
