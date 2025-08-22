// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl.reworked

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.platform.eel.spawnProcess
import com.intellij.terminal.completion.spec.ShellCommandExecutor
import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.util.execution.ParametersListUtil
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.cancellation.CancellationException

internal class ShellCommandExecutorReworked(private val eelDescriptor: EelDescriptor) : ShellCommandExecutor {
  override suspend fun runShellCommand(directory: String, command: String): ShellCommandResult {
    val commandList = ParametersListUtil.parse(command)
    val commandName = commandList.firstOrNull() ?: return emptyResult()
    val arguments = commandList.drop(1)
    return executeCommandViaEel(directory, commandName, arguments)
  }

  private suspend fun executeCommandViaEel(
    directory: String,
    commandName: String,
    arguments: List<String>,
  ): ShellCommandResult = coroutineScope {
    val scope = this

    val eelDirectory = try {
      EelPath.parse(directory, eelDescriptor)
    }
    catch (e: Exception) {
      LOG.error("Failed to parse directory as EelPath: '$directory'", e)
      return@coroutineScope emptyResult()
    }

    try {
      val eel = eelDescriptor.toEelApi()
      val process = eel.exec
        .spawnProcess(commandName)
        .args(arguments)
        .workingDirectory(eelDirectory)
        .scope(scope) // Terminate the process if the coroutine was canceled
        .eelIt()
      val result = process.awaitProcessResult()
      ShellCommandResult.create(result.stdoutString, result.exitCode)
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: ExecuteProcessException) {
      LOG.error("Failed to execute command using Eel API. Command: '$commandName', arguments: $arguments, directory: '$directory'", e)
      emptyResult()
    }
  }

  private fun emptyResult(): ShellCommandResult = ShellCommandResult.create("{}", 0)

  companion object {
    private val LOG = logger<ShellCommandExecutorReworked>()
  }
}