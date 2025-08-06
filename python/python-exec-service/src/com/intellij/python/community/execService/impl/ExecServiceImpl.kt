// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.python.community.execService.*
import com.intellij.python.community.execService.impl.processLaunchers.*
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ExecError
import com.jetbrains.python.errorProcessing.ExecErrorReason
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.failure
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.Nls


internal object ExecServiceImpl : ExecService {

  override suspend fun <T> executeAdvanced(binary: BinaryToExec, args: Args, options: ExecOptions, processInteractiveHandler: ProcessInteractiveHandler<T>): PyResult<T> {

    return coroutineScope {
      val request = LaunchRequest(this, args, options.env)
      val processLauncher: ProcessLauncher = when (binary) {
        is BinOnEel -> createProcessLauncherOnEel(binary, request)
        is BinOnTarget -> createProcessLauncherOnTarget(binary, request).getOr { return@coroutineScope it }
      }

      val description = options.processDescription
                        ?: PyExecBundle.message("py.exec.defaultName.process", (listOf(processLauncher.exeForError.toString()) + processLauncher.args).joinToString(" "))
      val process = processLauncher.start().getOr {
        val message = PyExecBundle.message("py.exec.start.error", description, it.error.cantExecProcessError, it.error.errNo
                                                                                                              ?: "unknown")
        return@coroutineScope processLauncher.createExecError(
          messageToUser = message,
          errorReason = it.error
        )
      }

      val result = try {
        withTimeout(options.timeout) {
          val interactiveResult = processInteractiveHandler.getResultFromProcess(binary, processLauncher.args, process)

          val successResult = interactiveResult.getOr { failure ->
            val (output, customErrorMessage) = failure.error
            val additionalMessage = customErrorMessage ?: run {
              PyExecBundle.message("py.exec.exitCode.error", description, output.exitCode)
            }
            return@withTimeout processLauncher.createExecError(
              messageToUser = additionalMessage,
              errorReason = ExecErrorReason.UnexpectedProcessTermination(output)
            )
          }
          Result.success(successResult)
        }
      }
      catch (_: TimeoutCancellationException) {
        processLauncher.killAndJoin()
        processLauncher.createExecError(
          messageToUser = PyExecBundle.message("py.exec.timeout.error", description, options.timeout),
          errorReason = ExecErrorReason.Timeout
        )
      }
      return@coroutineScope result
    }
  }
}

private fun ProcessLauncher.createExecError(messageToUser: @Nls String, errorReason: ExecErrorReason): Result.Failure<ExecError> =
  ExecError(
    exe = exeForError,
    args = args.toTypedArray(),
    additionalMessageToUser = messageToUser,
    errorReason = errorReason
  ).logAndFail()


private fun ExecError.logAndFail(): Result.Failure<ExecError> {
  fileLogger().warn(message)
  return failure(this)
}
