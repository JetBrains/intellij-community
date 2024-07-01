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
   * User typed prefix of the current value we are trying to complete.
   */
  val typedPrefix: String

  val shellName: ShellName

  /**
   * Runs the internal user invisible [command] in the current terminal session and returns the [ShellCommandResult].
   */
  suspend fun runShellCommand(@Language("ShellScript") command: String): ShellCommandResult

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
