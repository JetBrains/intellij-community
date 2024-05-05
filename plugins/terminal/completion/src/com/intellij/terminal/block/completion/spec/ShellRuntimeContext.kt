package com.intellij.terminal.block.completion.spec

import com.intellij.openapi.util.Key
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ShellRuntimeContext {
  val currentDirectory: String
  val commandText: String
  val typedPrefix: String
  val shellName: ShellName

  suspend fun runShellCommand(@Language("ShellScript") command: String): ShellCommandResult

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