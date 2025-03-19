package com.intellij.terminal.completion.spec

import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.NonExtendable
fun interface ShellCommandExecutor {
  /**
   * Simple basic interface to
   * Runs the command in Shell and returns the result.
   * Whether it is a visible user command or hidden generator command or mock execution etc. is decided by implementation.
   */
  suspend fun runShellCommand(@Language("ShellScript") command: String): ShellCommandResult
}
