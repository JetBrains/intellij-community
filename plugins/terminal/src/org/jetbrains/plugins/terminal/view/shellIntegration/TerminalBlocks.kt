// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel

/**
 * The terminal block is the range of text in the [TerminalOutputModel] and some additional information
 * about the content and meaning of this text.
 *
 * Currently, there is a single implementation: [TerminalCommandBlock].
 * So, use safe cast to [TerminalCommandBlock] if you need to work with the command block.
 * But other implementations might be added in the future.
 *
 * Blocks are supported only for the [regular][org.jetbrains.plugins.terminal.view.TerminalOutputModelsSet.regular] output model,
 * so all [TerminalOffset]'s are specified relative to it.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalBlockBase {
  val id: TerminalBlockId

  /**
   * Always check that this offset is in the bounds of the regular [TerminalOutputModel] before accessing the text.
   * Because when the output model starts trimming the start of the output (because of reaching the max length),
   * the block offsets are not updated.
   */
  val startOffset: TerminalOffset

  val endOffset: TerminalOffset
}

/**
 * The terminal block that represents the range of the terminal output that can contain
 * prompt, command and the command output.
 * Also, it provides additional metadata about the command, such as [workingDirectory], [executedCommand] and [exitCode].
 *
 * The usual structure of the block is the following:
 * ```
 * <startOffset>prompt: <commandStartOffset>some command
 * <outputStartOffset>some
 * command
 * output
 * <endOffset>
 * ```
 *
 * Note that the shell output can also contain the right prompt and line continuations.
 * It is worth detecting the positions of these parts as well, but it is not supported at the moment.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalCommandBlock : TerminalBlockBase {
  /**
   * The offset where the prompt finishes and command text starts.
   * Can be null in the initial block of the output before the first prompt is printed.
   *
   * Always check that this offset is in the bounds of the regular [TerminalOutputModel] before accessing the text.
   * Because when the output model starts trimming the start of the output (because of reaching the max length),
   * the block offsets are not updated.
   */
  val commandStartOffset: TerminalOffset?

  /**
   * The offset where the command output starts.
   *
   * Can be null if the command was not started to execute.
   * For example, if a user is typing a command now.
   * Or if the command text was blank and shell just printed the new prompt.
   * Or if the user aborted the command typing by pressing Ctrl+C.
   * If command was executed, but produced no output, then this offset will be the same as [endOffset].
   *
   * Always check that this offset is in the bounds of the regular [TerminalOutputModel] before accessing the text.
   * Because when the output model starts trimming the start of the output (because of reaching the max length),
   * the block offsets are not updated.
   */
  val outputStartOffset: TerminalOffset?

  /**
   * The absolute OS-dependent path to the working directory that was set in a shell
   * when this command block was active.
   */
  val workingDirectory: String?

  /**
   * The command text reported by the shell integration right before it is started.
   * Null if the command was not executed.
   */
  val executedCommand: String?

  /**
   * The exit code reported by the shell integration when the command execution was finished.
   * Null if the command was not executed.
   */
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
 * If the command was executed but produced no output, an empty string will be returned.
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
 * @return true if the command was started to execute in this block, so it can contain some output.
 */
@get:ApiStatus.Experimental
val TerminalCommandBlock.wasExecuted: Boolean
  get() = outputStartOffset != null