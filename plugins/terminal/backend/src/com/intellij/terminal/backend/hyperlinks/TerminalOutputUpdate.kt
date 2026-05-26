package com.intellij.terminal.backend.hyperlinks

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOffset

@ApiStatus.Internal
sealed interface TerminalOutputUpdate

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
  val modificationStamp: Long,
) : TerminalOutputUpdate

/**
 * A trim signal: everything below [firstLine] / [startOffset] has been removed from the start of the document.
 */
@ApiStatus.Internal
data class TerminalOutputTrimmingUpdate(
  val firstLine: TerminalLineIndex,
  val startOffset: TerminalOffset,
  val endOffset: TerminalOffset,
  val modificationStamp: Long,
) : TerminalOutputUpdate