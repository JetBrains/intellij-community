// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.terminal.block.session.StyleRange

/**
 * Model that should manage the content of the editor: text, highlightings, and cursor position.
 */
internal interface TerminalOutputModel {
  val editor: EditorEx

  /**
   * Offset in the editor's document where the cursor is located now.
   */
  val cursorOffsetState: StateFlow<Int>

  /**
   * [absoluteLineIndex] is the index of the line from the start of the terminal output.
   */
  @RequiresEdt
  @RequiresWriteLock
  fun updateContent(absoluteLineIndex: Int, text: String, styles: List<StyleRange>)

  /**
   * [absoluteLineIndex] is the index of the line from the start of the terminal output.
   */
  fun updateCursorPosition(absoluteLineIndex: Int, columnIndex: Int)
}