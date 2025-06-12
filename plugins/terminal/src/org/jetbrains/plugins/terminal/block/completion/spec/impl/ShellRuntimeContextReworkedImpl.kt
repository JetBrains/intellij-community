// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.platform.eel.spawnProcess
import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.terminal.completion.spec.ShellName
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.plugins.terminal.block.session.ShellIntegrationFunctions.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.Path

private val LOG: Logger = logger<ShellRuntimeContextReworkedImpl>()

class ShellRuntimeContextReworkedImpl(
  override val currentDirectory: String,
  override val typedPrefix: String,
  override val shellName: ShellName,
) : ShellRuntimeContext, UserDataHolderBase() {

  override suspend fun runShellCommand(command: String): ShellCommandResult {
    val commandList = ParametersListUtil.parse(command)
    var commandName = commandList.firstOrNull() ?: return ShellCommandResult.create("{}", 0)
    var arguments = commandList.drop(1)
    when {
      command.startsWith(GET_DIRECTORY_FILES.functionName) -> {
        commandName = "ls"
        val path = command.removePrefix(GET_DIRECTORY_FILES.functionName).trim()
        arguments = listOf<String>("-1ap", path)
      }
      command.startsWith(GET_ALIASES.functionName)
      || command.startsWith(GET_ENVIRONMENT.functionName) -> {
        return ShellCommandResult.create("{}", 0)
      }
    }
    return executeCommandViaEel(commandName, arguments)
  }

  private suspend fun executeCommandViaEel(commandName: String, arguments: List<String>): ShellCommandResult {
    try {
      val eelDescriptor: EelDescriptor = NioFiles.toPath(currentDirectory)?.getEelDescriptor() ?: LocalEelDescriptor
      val eel = eelDescriptor.toEelApi()
      val eelDirectory: EelPath = Path(currentDirectory).asEelPath()
      val processResult = eel.exec.spawnProcess(commandName).args(arguments).workingDirectory(eelDirectory).eelIt()
      val result = processResult.awaitProcessResult()
      return ShellCommandResult.create(result.stdoutString, result.exitCode)
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      LOG.error("Failed to access Eel API: ${e.message}", e)
      return ShellCommandResult.create("{}", 0)
    }
  }
}