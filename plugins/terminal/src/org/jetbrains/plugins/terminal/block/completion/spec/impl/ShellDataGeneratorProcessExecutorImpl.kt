// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.platform.eel.spawnProcess
import com.intellij.terminal.completion.spec.ShellCommandResult
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGeneratorProcessExecutor
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGeneratorProcessOptions
import kotlin.coroutines.cancellation.CancellationException

@ApiStatus.Internal
class ShellDataGeneratorProcessExecutorImpl(
  private val eelDescriptor: EelDescriptor,
  private val baseEnvVariables: Map<String, String>,
) : ShellDataGeneratorProcessExecutor {
  override suspend fun executeProcess(options: ShellDataGeneratorProcessOptions): ShellCommandResult = coroutineScope {
    val scope = this

    val eelDirectory = try {
      EelPath.parse(options.workingDirectory, eelDescriptor)
    }
    catch (e: Exception) {
      LOG.error("Failed to parse directory as EelPath: '${options.workingDirectory}'", e)
      return@coroutineScope emptyResult()
    }

    LOG.debug { "Executing process with options: $options" }

    try {
      val eel = eelDescriptor.toEelApi()
      val process = eel.exec
        .spawnProcess(options.executable)
        .args(options.args)
        .workingDirectory(eelDirectory)
        .env(baseEnvVariables + options.env)
        .interactionOptions(EelExecApi.RedirectStdErr(EelExecApi.RedirectTo.STDOUT))
        .scope(scope) // Terminate the process if the coroutine was canceled
        .eelIt()
      val result = process.awaitProcessResult()
      LOG.debug { "Process execution finished with exit code: ${result.exitCode}, options: $options" }
      ShellCommandResult.create(result.stdoutString, result.exitCode)
    }
    catch (ce: CancellationException) {
      LOG.debug { "Process execution canceled, options: $options" }
      throw ce
    }
    catch (e: ExecuteProcessException) {
      LOG.error("Failed to execute process using EelApi, options: $options", e)
      emptyResult()
    }
  }

  private fun emptyResult(): ShellCommandResult = ShellCommandResult.create("{}", 0)

  companion object {
    private val LOG = logger<ShellDataGeneratorProcessExecutorImpl>()
  }
}