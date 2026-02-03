package com.intellij.terminal.completion.spec

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ShellDataGeneratorProcessBuilder {
  /**
   * Additional arguments to be placed after the executable name in the command line.
   */
  fun args(args: List<String>): ShellDataGeneratorProcessBuilder

  /**
   * Additional arguments to be placed after the executable name in the command line.
   */
  fun args(vararg args: String): ShellDataGeneratorProcessBuilder = args(listOf(*args))

  /**
   * The absolute OS-dependent path to the working directory where the process should be executed.
   * If not specified, the current working directory of the shell process will be used.
   */
  fun workingDirectory(workingDirectory: String): ShellDataGeneratorProcessBuilder

  /**
   * Allows adding new or overriding existing environment variables for the process.
   */
  fun env(env: Map<String, String>): ShellDataGeneratorProcessBuilder

  /**
   * Executes the process taking into account specified options.
   */
  suspend fun execute(): ShellCommandResult
}