package com.intellij.terminal.completion.spec

import com.intellij.openapi.util.Key
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus

/**
 * The interface that represents the current context of the Shell command completion session.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ShellRuntimeContext {
  /**
   * Absolute **system independent** path of the current Shell directory.
   */
  val currentDirectory: String

  /**
   * Env variables used to start the shell process.
   *
   * Available only in the Reworked Terminal, empty map otherwise.
   */
  val envVariables: Map<String, String>

  /**
   * Words (tokens) of the command we are trying to complete.
   * The last token is the [typedPrefix].
   *
   * For example: `[ls, -al, ~/Documents/projects]`
   */
  val commandTokens: List<String>

  /**
   * User-typed prefix of the current token we are trying to complete. Without starting quotes.
   *
   * For example, if [commandTokens] are `[ls, -al, "~/Documents/proj]`,
   * then typed prefix is `~/Documents/proj`
   */
  val typedPrefix: String

  /**
   * **Supported only in Experimental 2024 Terminal.**
   * In the Reworked Terminal it will throw [UnsupportedOperationException].
   *
   * Do not use it in new code.
   * It is expected that completion items computation logic shouldn't depend on the shell.
   */
  @get:ApiStatus.Obsolete
  val shellName: ShellName

  /**
   * This method was initially designed for the Experimental 2024 Terminal, which is now deprecated.
   * It was running the internal user invisible [command] in the current terminal session.
   *
   * Current status of this method:
   * 1. Experimental 2024 Terminal - should work as designed.
   * 2. Reworked Terminal - will work only if [command] can be interpreted as OS process command line (bash syntax won't work),
   * because Reworked Terminal can only execute OS processes in the same environment where shell is running.
   *
   * Prefer using [createProcessBuilder] instead if you write the new code, that is OK to work only in the Reworked Terminal.
   * This method can be used only in the existing command specifications that should work in the Experimental 2024 Terminal as well.
   */
  @ApiStatus.Obsolete
  suspend fun runShellCommand(@Language("ShellScript") command: String): ShellCommandResult

  /**
   * Creates the builder for process execution in the environment where the current terminal session is running.
   * Modify the builder parameters and call [ShellDataGeneratorProcessBuilder.execute] to execute the process.
   *
   * **Works only in the Reworked Terminal.**
   * In Experimental 2024 Terminal it will throw [UnsupportedOperationException].
   *
   * @param executable the absolute path the executable or the executable name to be found in the PATH environment variable.
   */
  suspend fun createProcessBuilder(executable: String): ShellDataGeneratorProcessBuilder

  /**
   * **Works only in the Reworked Terminal.**
   * In Experimental 2024 Terminal it will throw [UnsupportedOperationException].
   *
   * @param path absolute os-dependent path to the directory.
   */
  suspend fun listDirectoryFiles(path: String): List<ShellFileInfo>

  /**
   * Used to implement custom extensions of [ShellRuntimeContext].
   * See the extensions in this [file][org.jetbrains.plugins.terminal.block.completion.spec.getFileSuggestions]
   */
  fun <T> getUserData(key: Key<T>): T?
}

@ApiStatus.Experimental
class ShellCommandResult private constructor(val output: String, val exitCode: Int) {
  companion object {
    fun create(output: String, exitCode: Int): ShellCommandResult = ShellCommandResult(output, exitCode)
  }

  override fun toString(): String {
    return "ShellCommandResult(output='$output', exitCode=$exitCode)"
  }
}
