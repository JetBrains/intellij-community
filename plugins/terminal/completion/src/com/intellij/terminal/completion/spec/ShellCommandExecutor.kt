package com.intellij.terminal.completion.spec

import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.NonExtendable
interface ShellCommandExecutor {
  /**
   * Runs the shell command and returns the result.
   * How this command is executed depends on the implementation.
   */
  suspend fun runShellCommand(directory: String, @Language("ShellScript") command: String): ShellCommandResult
}
