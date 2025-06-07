// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.eel.spawnProcess
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.ProcessInteractiveHandler
import com.intellij.python.community.execService.WhatToExec
import com.jetbrains.python.PythonHelpersLocator
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ExecError
import com.jetbrains.python.errorProcessing.ExecErrorReason
import com.jetbrains.python.errorProcessing.PyExecResult
import com.jetbrains.python.errorProcessing.failure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.CheckReturnValue
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import kotlin.time.Duration


internal object ExecServiceImpl : ExecService {
  override suspend fun <T> execute(
    whatToExec: WhatToExec,
    args: List<String>,
    options: ExecOptions,
    processInteractiveHandler: ProcessInteractiveHandler<T>,
  ): PyExecResult<T> {
    val executableProcess = whatToExec.buildExecutableProcess(args, options)
    val eelProcess = executableProcess.run().getOr { return it }

    val result = try {
      withTimeout(options.timeout) {
        val interactiveResult = processInteractiveHandler.getResultFromProcess(whatToExec, args, eelProcess)

        val successResult = interactiveResult.getOr { failure ->
          val (output, customErrorMessage) = failure.error
          return@withTimeout executableProcess.failAsExecutionFailed(ExecErrorReason.UnexpectedProcessTermination(output), customErrorMessage)
        }
        Result.success(successResult)
      }
    }
    catch (_: TimeoutCancellationException) {
      executableProcess.killProcessAndFailAsTimeout(eelProcess, options.timeout)
    }

    return result
  }
}

private data class EelExecutableProcess(
  val exe: EelPath,
  val args: List<String>,
  val env: Map<String, String>,
  val workingDirectory: Path?,
  val description: @Nls String,
)

private suspend fun WhatToExec.buildExecutableProcess(args: List<String>, options: ExecOptions): EelExecutableProcess {
  val (exe, args) = when (this) {
    is WhatToExec.Binary -> Pair(binary, args)
    is WhatToExec.Helper -> {
      val eel = python.getEelDescriptor().toEelApi()
      val localHelper = withContext(Dispatchers.IO) { PythonHelpersLocator.findPathInHelpers(helper) }
      val remoteHelper = EelPathUtils.transferLocalContentToRemote(
        source = localHelper,
        target = EelPathUtils.TransferTarget.Temporary(eel.descriptor)
      ).asEelPath().toString()
      Pair(python, listOf(remoteHelper) + args)
    }
  }

  val description = options.processDescription ?: when (this) {
    is WhatToExec.Binary -> PyExecBundle.message("py.exec.defaultName.process")
    is WhatToExec.Helper -> PyExecBundle.message("py.exec.defaultName.helper")
  }

  return EelExecutableProcess(exe.asEelPath(), args, options.env, options.workingDirectory, description)
}

@CheckReturnValue
private suspend fun EelExecutableProcess.run(): PyExecResult<EelProcess> {
  val workingDirectory = if (workingDirectory != null && !workingDirectory.isAbsolute) workingDirectory.toRealPath() else workingDirectory
  try {
    val executionResult = exe.descriptor.toEelApi().exec.spawnProcess(exe.toString())
      .args(args)
      .env(env)
      .workingDirectory(workingDirectory?.asEelPath()).eelIt()

    return Result.success(executionResult)
  } catch (e: ExecuteProcessException) {
    return failAsCantStart(e)
  }
}

private fun EelExecutableProcess.failAsCantStart(executeProcessError: ExecuteProcessException): Result.Failure<ExecError> {
  return ExecError(
    exe = exe,
    args = args.toTypedArray(),
    additionalMessageToUser = PyExecBundle.message("py.exec.start.error", description, executeProcessError.message, executeProcessError.errno),
    errorReason = ExecErrorReason.CantStart(executeProcessError.errno, executeProcessError.message)
  ).logAndFail()
}

private suspend fun EelExecutableProcess.killProcessAndFailAsTimeout(eelProcess: EelProcess, timeout: Duration): Result.Failure<ExecError> {
  eelProcess.kill()

  return ExecError(
    exe = exe,
    args = args.toTypedArray(),
    additionalMessageToUser = PyExecBundle.message("py.exec.timeout.error", description, timeout),
    errorReason = ExecErrorReason.Timeout
  ).logAndFail()
}

private fun EelExecutableProcess.failAsExecutionFailed(processOutput: ExecErrorReason.UnexpectedProcessTermination, customMessage: @Nls String?): Result.Failure<ExecError> {
  val additionalMessage = customMessage ?: run {
    PyExecBundle.message("py.exec.exitCode.error", description, processOutput.exitCode)
  }

  return ExecError(
    exe = exe,
    args = args.toTypedArray(),
    additionalMessageToUser = additionalMessage,
    errorReason = processOutput
  ).logAndFail()
}

private fun ExecError.logAndFail(): Result.Failure<ExecError> {
  fileLogger().warn(message)
  return failure(this)
}