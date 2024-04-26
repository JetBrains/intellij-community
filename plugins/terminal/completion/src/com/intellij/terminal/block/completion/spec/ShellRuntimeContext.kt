package com.intellij.terminal.block.completion.spec

import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ShellRuntimeContext {
  val currentDirectory: String
  val commandText: String
  val typedPrefix: String

  suspend fun runShellCommand(@Language("ShellScript") command: String): ShellCommandResult
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