// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration

import com.intellij.openapi.Disposable
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

/**
 * Declares the features provided by the terminal shell integration (when it is available).
 *
 * When we start the shell process, we inject our shell scripts into its startup.
 * To get the information about the environment and subscribe for events.
 * For example, to know the positions of the prompt, command and the command output in the shell output.
 *
 * Currently, the shell integration is available only for **Bash, Zsh and PowerShell**.
 * But in Zsh the integration is disabled when the PowerLevel10K plugin is installed.
 * Also, it is controlled by the option in the Terminal settings.
 * ([org.jetbrains.plugins.terminal.TerminalOptionsProvider.shellIntegration])
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalShellIntegration {
  /**
   * The model that holds the information ranges of the shell output.
   *
   * @see TerminalBlockBase
   * @see TerminalCommandBlock
   */
  val blocksModel: TerminalBlocksModel

  /**
   * Represents the current state of the terminal during shell output processing.
   *
   * StateFlow can be useful to await some particular state in a suspending context,
   * for example `outputStatus.first { it == TypingCommand }`.
   */
  val outputStatus: StateFlow<TerminalOutputStatus>

  /**
   * Allows listening for command start and finish events.
   */
  fun addCommandExecutionListener(parentDisposable: Disposable, listener: TerminalCommandExecutionListener)

  /**
   * Allows listening for results of shell-based completion.
   */
  @ApiStatus.Internal
  fun addShellBasedCompletionListener(parentDisposable: Disposable, listener: TerminalShellBasedCompletionListener)
}