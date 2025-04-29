// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.*
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.*
import com.intellij.python.community.execService.*
import com.jetbrains.python.PythonHelpersLocator
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ExecError
import com.jetbrains.python.errorProcessing.ExecErrorReason
import com.jetbrains.python.errorProcessing.asExecutionFailed
import com.jetbrains.python.errorProcessing.failure
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.CheckReturnValue
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import kotlin.time.Duration


internal object ExecServiceImpl : ExecService {
  override suspend fun <T> executeInteractive(
    whatToExec: WhatToExec,
    args: List<String>,
    options: ExecOptions,
    eelProcessInteractiveHandler: EelProcessInteractiveHandler<T>,
  ): Result<T, ExecError> {
    val executableProcess = whatToExec.buildExecutableProcess(args, options)
    val eelProcess = executableProcess.run().getOr { return it }

    val result = try {
      withTimeout(options.timeout) {
        val interactiveResult = eelProcessInteractiveHandler.invoke(eelProcess)
        val exitProcessOutput = eelProcess.awaitProcessResult().asPlatformOutput()

        val successResult = interactiveResult.getOr { failure ->
          return@withTimeout executableProcess.failAsExecutionFailed(exitProcessOutput, failure.error)
        }
        Result.success(successResult)
      }
    }
    catch (_: TimeoutCancellationException) {
      executableProcess.killProcessAndFailAsTimeout(eelProcess, options.timeout)
    }

    return result
  }

  override suspend fun <T> execute(
    whatToExec: WhatToExec,
    args: List<String>,
    options: ExecOptions,
    processOutputTransformer: ProcessOutputTransformer<T>,
  ): Result<T, ExecError> {
    val executableProcess = whatToExec.buildExecutableProcess(args, options)
    val eelProcess = executableProcess.run().getOr { return it }

    val eelProcessExecutionResult = try {
      withTimeout(options.timeout) { eelProcess.awaitProcessResult() }
    }
    catch (_: TimeoutCancellationException) {
      return executableProcess.killProcessAndFailAsTimeout(eelProcess, options.timeout)
    }

    val processOutput = eelProcessExecutionResult.asPlatformOutput()
    val transformerSuccess = processOutputTransformer.invoke(processOutput).getOr { failure ->
      return executableProcess.failAsExecutionFailed(processOutput, failure.error)
    }
    return Result.success(transformerSuccess)
  }
}

private data class EelExecutableProcess(
  val eel: EelApi,
  val exe: String,
  val args: List<String>,
  val env: Map<String, String>,
  val workingDirectory: Path?,
  val description: @Nls String,
)

private suspend fun WhatToExec.buildExecutableProcess(args: List<String>, options: ExecOptions): EelExecutableProcess {
  val (eel, exe, args) = when (this) {
    is WhatToExec.Binary -> Triple(binary.getEelDescriptor().upgrade(), binary.asEelPath().toString(), args)
    is WhatToExec.Helper -> {
      val eel = python.getEelDescriptor().upgrade()
      val localHelper = PythonHelpersLocator.findPathInHelpers(helper)
                        ?: error("No ${helper} found: installation broken?")
      val remoteHelper = EelPathUtils.transferLocalContentToRemote(
        source = localHelper,
        target = EelPathUtils.TransferTarget.Temporary(eel.descriptor)
      ).asEelPath().toString()
      Triple(eel, python.asEelPath().toString(), listOf(remoteHelper) + args)
    }
    is WhatToExec.Command -> Triple(eel, command, args)
  }

  val description = options.processDescription ?: when (this) {
    is WhatToExec.Binary -> PyExecBundle.message("py.exec.defaultName.process")
    is WhatToExec.Helper -> PyExecBundle.message("py.exec.defaultName.helper")
    is WhatToExec.Command -> PyExecBundle.message("py.exec.defaultName.command")
  }

  return EelExecutableProcess(eel, exe, args, options.env, options.workingDirectory, description)
}

@CheckReturnValue
private suspend fun EelExecutableProcess.run(): Result<EelProcess, ExecError> {
  val workingDirectory = if (workingDirectory != null && !workingDirectory.isAbsolute) workingDirectory.toRealPath() else workingDirectory
  val executionResult = eel.exec.execute(exe)
    .args(args)
    .env(env)
    .workingDirectory(workingDirectory?.asEelPath()).eelIt()

  val process = executionResult.getOr { err ->
    return failAsCantStart(err.error)
  }
  return Result.success(process)
}

private fun EelExecutableProcess.failAsCantStart(executeProcessError: EelExecApi.ExecuteProcessError): Result.Failure<ExecError> {
  return ExecError(
    command = arrayOf(exe) + args.toTypedArray(),
    additionalMessageToUser = PyExecBundle.message("py.exec.start.error", description, executeProcessError.message, executeProcessError.errno),
    errorReason = ExecErrorReason.CantStart(executeProcessError.errno, executeProcessError.message)
  ).logAndFail()
}

private suspend fun EelExecutableProcess.killProcessAndFailAsTimeout(eelProcess: EelProcess, timeout: Duration): Result.Failure<ExecError> {
  eelProcess.kill()

  return ExecError(
    command = arrayOf(exe) + args.toTypedArray(),
    additionalMessageToUser = PyExecBundle.message("py.exec.timeout.error", description, timeout),
    errorReason = ExecErrorReason.Timeout
  ).logAndFail()
}

private fun EelExecutableProcess.failAsExecutionFailed(processOutput: ProcessOutput, customMessage: @Nls String?): Result.Failure<ExecError> {
  val additionalMessage = customMessage ?: run {
    PyExecBundle.message("py.exec.exitCode.error", description, processOutput.exitCode)
  }

  return ExecError(
    command = arrayOf(exe) + args.toTypedArray(),
    additionalMessageToUser = additionalMessage,
    errorReason = processOutput.asExecutionFailed()
  ).logAndFail()
}

private fun ExecError.logAndFail(): Result.Failure<ExecError> {
  fileLogger().warn(message)
  return failure(this)
}


private fun EelProcessExecutionResult.asPlatformOutput(): ProcessOutput = ProcessOutput(stdoutString, stderrString, exitCode, false, false)