package org.jetbrains.plugins.terminal.hyperlinks

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOffset

@ApiStatus.Internal
data class TerminalOutputContentUpdate(
  /** The text for lines [startLine]..[endLine] */
  val charsSequence: CharSequence,
  /** The absolute line index of the first character of [charsSequence] in the terminal output */
  val startLine: TerminalLineIndex,
  /** The absolute line index (inclusive) of the last character of [charsSequence] in the terminal output */
  val endLine: TerminalLineIndex,
  /** The absolute offset of the first character of [charsSequence] in the terminal output */
  val startOffset: TerminalOffset,
  /** The absolute index of the first line that remains in the output after trimming */
  val trimStartLine: TerminalLineIndex,
  /** The absolute offset of the first character that remains after trimming */
  val trimStartOffset: TerminalOffset,
  val modificationStamp: Long,
)