package com.intellij.terminal.frontend

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.LineWrapPositionStrategy
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * The Terminal requires the simplest line wrapping logic:
 * part of the logical line that doesn't fit into the width, should be moved to the next visual line.
 * So, actually, it is a hard wrap.
 */
@ApiStatus.Internal
class TerminalLineWrapPositionStrategy : LineWrapPositionStrategy {
  /**
   * By default, disallows breaking before low surrogate characters to prevent break inside of surrogate pairs.
   */
  override fun canWrapLineAtOffset(text: CharSequence, offset: Int): Boolean {
    val c: Char = text[offset]
    // Ensure no break occurs within surrogate pairs.
    if (Character.isLowSurrogate(c)) {
      if (offset - 1 >= 0 && Character.isHighSurrogate(text.get(offset - 1))) {
        return false
      }
    }
    return true
  }

  /**
   * This method is not reachable
   * because the valid offset will be found in [canWrapLineAtOffset] method
   */
  override fun calculateWrapPosition(
    document: Document,
    project: Project?,
    startOffset: Int,
    endOffset: Int,
    maxPreferredOffset: Int,
    allowToBeyondMaxPreferredOffset: Boolean,
    isSoftWrap: Boolean,
  ): Int {
    // Wrap after the last character that fits into the required width
    return maxPreferredOffset - 1
  }
}