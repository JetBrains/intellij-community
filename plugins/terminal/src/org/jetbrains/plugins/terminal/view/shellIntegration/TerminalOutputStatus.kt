// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration

import org.jetbrains.annotations.ApiStatus

/**
 * Represents the state of the terminal during shell output processing.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalOutputStatus {
  /**
   * The terminal is waiting until the shell prompt is received from the output.
   *
   * For example, this status is active when the shell is just started and didn't print the first prompt yet.
   * Or when we start receiving the prompt text after the command execution.
   */
  object WaitingForPrompt : TerminalOutputStatus

  /**
   * The prompt was received and user is invited to type the command.
   */
  object TypingCommand : TerminalOutputStatus

  /**
   * User typed the command and pressed Enter to execute it.
   * Now the output of the command is being received.
   */
  object ExecutingCommand : TerminalOutputStatus
}