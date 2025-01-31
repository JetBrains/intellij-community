// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.executeProcess
import com.intellij.platform.eel.getOr
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.*
import com.intellij.python.community.execService.ExecService
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
import kotlin.time.Duration


internal object ExecServiceImpl : ExecService {
  override suspend fun execGetStdout(whatToExec: WhatToExec, args: List<String>, processDescription: @Nls String?, timeout: Duration): Result<String, ExecException> {
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
    val processDescription = processDescription ?: when (whatToExec) {
      is WhatToExec.Binary -> PyExecBundle.message("py.exec.defaultName.process")
      is WhatToExec.Helper -> PyExecBundle.message("py.exec.defaultName.helper")
      is WhatToExec.Command -> PyExecBundle.message("py.exec.defaultName.command")
    }
    return eel.execGetStdoutImpl(exe, args, processDescription, timeout)
  }
}


@ApiStatus.Internal
@CheckReturnValue
private suspend fun EelApi.execGetStdoutImpl(
  exe: String,
  args: List<String>,
  processDescription: @Nls String,
  timeout: Duration,
): Result<String, ExecException> {
  val process = exec.executeProcess(exe, *args.toTypedArray()).getOr { err ->
    val text = PyExecBundle.message("py.exec.start.error", processDescription, err.error.message, err.error.errno)
    val failure = PyExecFailureImpl(exe, args, text, FailureReason.CantStart)
    fileLogger().warn(failure.userMessage)
    return failure(failure)
  }
  val result = withTimeoutOrNull(timeout) {
    process.awaitProcessResult()
  }
  if (result == null) {
    process.kill()
    val text = PyExecBundle.message("py.exec.timeout.error", processDescription, timeout)
    val failure = PyExecFailureImpl(exe, args, text, FailureReason.CantStart)
    fileLogger().info(failure.userMessage)
    return failure(failure)
  }
  return if (result.exitCode == 0) {
    Result.success(result.stdoutString)
  }
  else {
    val text = PyExecBundle.message("py.exec.exitCode.error", processDescription, result.exitCode)
    val failure = PyExecFailureImpl(exe, args, text, FailureReason.ExecutionFailed(result.asPlatformOutput()))
    fileLogger().warn(failure.userMessage)
    failure(failure)
  }
}

private fun EelProcessExecutionResult.asPlatformOutput(): ProcessOutput = ProcessOutput(stdoutString, stderrString, exitCode, false, false)