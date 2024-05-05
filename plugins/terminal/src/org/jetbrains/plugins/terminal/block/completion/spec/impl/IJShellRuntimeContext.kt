// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.terminal.block.completion.spec.ShellCommandResult
import com.intellij.terminal.block.completion.spec.ShellName
import com.intellij.terminal.block.completion.spec.ShellRuntimeContext

internal class IJShellRuntimeContext(
  override val currentDirectory: String,
  override val commandText: String,
  override val typedPrefix: String,
  override val shellName: ShellName,
  private val generatorCommandsRunner: ShellGeneratorCommandsRunner
) : ShellRuntimeContext {

  override suspend fun runShellCommand(command: String): ShellCommandResult {
    return generatorCommandsRunner.runGeneratorCommand(command)
  }

  override fun toString(): String {
    return "IJShellRuntimeContext(currentDirectory='$currentDirectory', commandText='$commandText', typedPrefix='$typedPrefix')"
  }
}