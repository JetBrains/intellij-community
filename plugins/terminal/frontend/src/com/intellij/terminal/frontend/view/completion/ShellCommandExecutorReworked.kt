// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.completion

import com.intellij.terminal.completion.spec.ShellCommandExecutor
import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGeneratorProcessExecutor
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGeneratorProcessOptionsImpl

@ApiStatus.Internal
class ShellCommandExecutorReworked(
  private val delegate: ShellDataGeneratorProcessExecutor,
) : ShellCommandExecutor {
  override suspend fun runShellCommand(directory: String, command: String): ShellCommandResult {
    val commandList = ParametersListUtil.parse(command)
    val commandName = commandList.firstOrNull() ?: return emptyResult()
    val arguments = commandList.drop(1)
    val options = ShellDataGeneratorProcessOptionsImpl(
      executable = commandName,
      args = arguments,
      workingDirectory = directory,
      env = emptyMap()
    )
    return delegate.executeProcess(options)
  }

  private fun emptyResult(): ShellCommandResult = ShellCommandResult.create("{}", 0)
}