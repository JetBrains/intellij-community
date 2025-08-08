// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.python.community.execService.*
import com.intellij.python.community.execService.impl.processLaunchers.LaunchRequest
import com.intellij.python.community.execService.impl.processLaunchers.ProcessLauncher
import com.intellij.python.community.execService.impl.processLaunchers.createProcessLauncherOnEel
import com.intellij.python.community.execService.impl.processLaunchers.createProcessLauncherOnTarget
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.Nls


internal object ExecServiceImpl : ExecService {

  override suspend fun executeGetProcess(binary: BinaryToExec, args: Args, scopeToBind: CoroutineScope?, options: ExecGetProcessOptions): Result<Process, ExecuteGetProcessError<*>> {
    val launcher = create(binary, args, options, scopeToBind).getOr { return it }
    val process = launcher.start().getOr {
      val createExecError = launcher.createExecError(options.processDescription ?: "", it.error).error
      return Result.failure(ExecuteGetProcessError.CanStart(createExecError))
    }
    return Result.success(process)
  }

  private suspend fun create(binary: BinaryToExec, args: Args, options: ExecOptionsBase, scopeToBind: CoroutineScope? = null): Result<ProcessLauncher, ExecuteGetProcessError.EnvironmentError> {
    val scope = scopeToBind ?: ApplicationManager.getApplication().service<MyService>().scope
    val request = LaunchRequest(scope, args, options.env, options.tty)
    return Result.success(
      when (binary) {
        is BinOnEel -> createProcessLauncherOnEel(binary, request)
        is BinOnTarget -> createProcessLauncherOnTarget(binary, request).getOr {
          options.processDescription?.let { message ->
            it.error.pyError.addMessage(message) // TODO: 18n
          }
          return it
        }
      })
  }

  override suspend fun <T> executeAdvanced(binary: BinaryToExec, args: Args, options: ExecOptions, processInteractiveHandler: ProcessInteractiveHandler<T>): PyResult<T> {
    return coroutineScope {
      val processLauncher = create(binary, args, options, this).getOr {
        return@coroutineScope it.asPyError()
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

private fun <T : ExecErrorReason> ProcessLauncher.createExecError(messageToUser: @Nls String, errorReason: T): Result.Failure<ExecErrorImpl<T>> =
  ExecErrorImpl(
    exe = exeForError,
    args = args.toTypedArray(),
    additionalMessageToUser = messageToUser,
    errorReason = errorReason
  ).logAndFail()


private fun <T : ExecErrorReason> ExecErrorImpl<T>.logAndFail(): Result.Failure<ExecErrorImpl<T>> {
  fileLogger().warn(message)
  return failure(this)
}

@Service
private class MyService(val scope: CoroutineScope)

private fun Result.Failure<ExecuteGetProcessError<*>>.asPyError(): Result.Failure<PyError> = PyResult.failure(this.error.pyError)