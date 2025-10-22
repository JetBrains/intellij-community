// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalBlockBase {
  val id: TerminalBlockId
  val startOffset: TerminalOffset
  val endOffset: TerminalOffset
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalCommandBlock : TerminalBlockBase {
  val commandStartOffset: TerminalOffset?
  val outputStartOffset: TerminalOffset?

  val workingDirectory: String?
  /**
   * Should be non-null if the command was started to execute.
   * It is the command text reported by the shell integration right before it is started.
   */
  val executedCommand: String?
  val exitCode: Int?
}

/**
 * @param model regular terminal output model (not alternative one)
 * @return the text without trailing whitespaces that was typed at the place of the command.
 * Can return null if this block doesn't contain the command
 * or if the command became partial out of model bounds because of trimming.
 *
 * **Note**: the returned text might be different from [TerminalCommandBlock.executedCommand], because currently,
 * we do not detect the right part of the prompt and inline completion grey text (provided by the shell plugins).
 * But this method can be called before command execution is started to get the currently typed command.
 */
@ApiStatus.Experimental
fun TerminalCommandBlock.getTypedCommandText(model: TerminalOutputModel): String? {
  val start = commandStartOffset ?: return null
  val end = outputStartOffset ?: endOffset
  if (start < model.startOffset || start > model.endOffset
      || end < model.startOffset || end > model.endOffset
      || start > end) {
    return null
  }
  return model.getText(start, end).toString().trimEnd()
}

/**
 * @param model regular terminal output model (not alternative one)
 * @return command output text with possibly trimmed start and without trailing whitespaces.
 * Can return null if no command was running in this block or the block is out of model bounds.
 * If the command produces no output, an empty string will be returned.
 */
@ApiStatus.Experimental
fun TerminalCommandBlock.getOutputText(model: TerminalOutputModel): String? {
  val start = outputStartOffset?.coerceAtLeast(model.startOffset) ?: return null
  val end = endOffset
  if (start < model.startOffset || start > model.endOffset
      || end < model.startOffset || end > model.endOffset
      || start > end) {
    return null
  }
  return model.getText(start, end).toString().trimEnd()
}

/**
 * @return true if the command was started to execute in this block, so it contains some output.
 */
@get:ApiStatus.Experimental
val TerminalCommandBlock.wasExecuted: Boolean
  get() = outputStartOffset != null