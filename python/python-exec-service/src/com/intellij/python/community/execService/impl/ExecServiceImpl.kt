// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.execute
import com.intellij.platform.eel.getOr
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.*
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.WhatToExec
import com.jetbrains.python.PythonHelpersLocator
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError.ExecException
import com.jetbrains.python.errorProcessing.failure
import com.jetbrains.python.execution.FailureReason
import com.jetbrains.python.execution.userMessage
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue
import org.jetbrains.annotations.Nls
import kotlin.io.path.pathString

private val WhatToExec.defaultProcessName: @Nls String
  get() = when (this) {
    is WhatToExec.Binary -> PyExecBundle.message("py.exec.defaultName.process")
    is WhatToExec.Helper -> PyExecBundle.message("py.exec.defaultName.helper")
    is WhatToExec.Command -> PyExecBundle.message("py.exec.defaultName.command")
  }

internal object ExecServiceImpl : ExecService {
  override suspend fun <T> execute(
    whatToExec: WhatToExec,
    args: List<String>,
    options: ExecOptions,
    processOutputTransformer: ProcessOutputTransformer<T>,
  ): Result<T, ExecException> {
    val (eel, exe, args) = when (whatToExec) {
      is WhatToExec.Binary -> Triple(whatToExec.binary.getEelDescriptor().upgrade(), whatToExec.binary.pathString, args)
      is WhatToExec.Helper -> {
        val eel = whatToExec.python.getEelDescriptor().upgrade()
        val localHelper = PythonHelpersLocator.findPathInHelpers(whatToExec.helper)
                          ?: error("No ${whatToExec.helper} found: installation broken?")
        val remoteHelper = EelPathUtils.transferLocalContentToRemoteTempIfNeeded(eel, localHelper).toString()
        Triple(eel, whatToExec.python.pathString, listOf(remoteHelper) + args)
      }
      is WhatToExec.Command -> Triple(whatToExec.eel, whatToExec.command, args)
    }

    val processOutput = eel.execGetProcessOutputImpl(
      exe = exe,
      args = args,
      options = options,
      processDescription = options.processDescription ?: whatToExec.defaultProcessName
    ).getOr { return it }

    val transformerResult = processOutputTransformer.invoke(processOutput)
    val transformerSuccess = transformerResult.getOr { transformerFailure ->
      val additionalMessage = transformerFailure.error ?: run {
        val actualProcessDescription = options.processDescription ?: whatToExec.defaultProcessName
        PyExecBundle.message("py.exec.exitCode.error", actualProcessDescription, processOutput.exitCode)
      }

      return PyExecFailureImpl(
        command = exe,
        args = args,
        additionalMessage = additionalMessage,
        failureReason = FailureReason.ExecutionFailed(processOutput)
      ).let {
        fileLogger().warn(it.userMessage)
        failure(it)
      }
    }
    return Result.success(transformerSuccess)
  }
}


@ApiStatus.Internal
@CheckReturnValue
private suspend fun EelApi.execGetProcessOutputImpl(
  exe: String,
  args: List<String>,
  options: ExecOptions,
  processDescription: @Nls String,
): Result<ProcessOutput, ExecException> {
  val workDirectoryEelPath = options.workingDirectory?.let { EelPath.parse(it.toString(), this.descriptor) }
  val executionResult = exec.execute(exe) {
    args(args)
    env(options.env)
    workingDirectory(workDirectoryEelPath)
  }

  val process = executionResult.getOr { err ->
    val text = PyExecBundle.message("py.exec.start.error", processDescription, err.error.message, err.error.errno)
    val failure = PyExecFailureImpl(exe, args, text, FailureReason.CantStart)
    fileLogger().warn(failure.userMessage)
    return failure(failure)
  }
  val result = withTimeoutOrNull(options.timeout) {
    process.awaitProcessResult()
  }
  if (result == null) {
    process.kill()
    val text = PyExecBundle.message("py.exec.timeout.error", processDescription, options.timeout)
    val failure = PyExecFailureImpl(exe, args, text, FailureReason.CantStart)
    fileLogger().info(failure.userMessage)
    return failure(failure)
  }

  return Result.success(result.asPlatformOutput())
}

private fun EelProcessExecutionResult.asPlatformOutput(): ProcessOutput = ProcessOutput(stdoutString, stderrString, exitCode, false, false)