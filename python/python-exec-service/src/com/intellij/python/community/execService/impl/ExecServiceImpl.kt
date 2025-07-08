// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.eel.spawnProcess
import com.intellij.python.community.execService.ArgsBuilder
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.ProcessInteractiveHandler
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.*
import kotlinx.coroutines.*
import org.jetbrains.annotations.CheckReturnValue
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.time.Duration


internal object ExecServiceImpl : ExecService {

  override suspend fun <T> executeAdvanced(binary: Path, argsBuilder: suspend ArgsBuilder.() -> Unit, options: ExecOptions, processInteractiveHandler: ProcessInteractiveHandler<T>): PyExecResult<T> {
    val args = ArgsBuilderImpl(binary.getEelDescriptor().toEelApi()).apply { argsBuilder() }.args
    val description = options.processDescription
                      ?: PyExecBundle.message("py.exec.defaultName.process", (listOf(binary.pathString) + args).joinToString(" "))

    return coroutineScope {

      val binary = if (binary.isAbsolute) binary else options.workingDirectory?.resolve(binary) ?: binary.toAbsolutePath()
      val eelPath = binary.asEelPath()
      val executableProcess = EelExecutableProcess(eelPath, args, options.env, options.workingDirectory, description)
      val eelProcess = executableProcess.run(this).getOr { return@coroutineScope it }
      val result = try {
        withTimeout(options.timeout) {
          val interactiveResult = processInteractiveHandler.getResultFromProcess(binary, args, eelProcess)

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

      return@coroutineScope result
    }
  }
}

private data class EelExecutableProcess(
  val exe: EelPath,
  val args: List<String>,
  val env: Map<String, String>,
  val workingDirectory: Path?,
  val description: @Nls String,
)

@CheckReturnValue
private suspend fun EelExecutableProcess.run(scopeToBound: CoroutineScope): PyExecResult<EelProcess> {
  val workingDirectory = if (workingDirectory != null && !workingDirectory.isAbsolute) workingDirectory.toRealPath() else workingDirectory
  try {
    val executionResult = exe.descriptor.toEelApi().exec.spawnProcess(exe.toString())
      .scope(scopeToBound)
      .args(args)
      .env(env)
      .workingDirectory(workingDirectory?.asEelPath())
      .eelIt()

    return Result.success(executionResult)
  }
  catch (e: ExecuteProcessException) {
    return failAsCantStart(e)
  }
}

private fun EelExecutableProcess.failAsCantStart(executeProcessError: ExecuteProcessException): Result.Failure<ExecError> {
  return ExecError(
    exe = Exe.OnEel(exe),
    args = args.toTypedArray(),
    additionalMessageToUser = PyExecBundle.message("py.exec.start.error", description, executeProcessError.message, executeProcessError.errno),
    errorReason = ExecErrorReason.CantStart(executeProcessError.errno, executeProcessError.message)
  ).logAndFail()
}

private suspend fun EelExecutableProcess.killProcessAndFailAsTimeout(eelProcess: EelProcess, timeout: Duration): Result.Failure<ExecError> {
  eelProcess.interrupt()
  eelProcess.kill()
  eelProcess.exitCode.await()

  return ExecError(
    exe = Exe.OnEel(exe),
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
    exe = Exe.OnEel(exe),
    args = args.toTypedArray(),
    additionalMessageToUser = additionalMessage,
    errorReason = processOutput
  ).logAndFail()
}

private fun ExecError.logAndFail(): Result.Failure<ExecError> {
  fileLogger().warn(message)
  return failure(this)
}

private class ArgsBuilderImpl(private val eel: EelApi) : ArgsBuilder {
  private val _args = CopyOnWriteArrayList<String>()
  val args: List<String> = _args
  override fun addArgs(vararg args: String) {
    _args.addAll(args)
  }

  override suspend fun addLocalFile(localFile: Path): Unit = withContext(Dispatchers.IO) {
    assert(localFile.exists()) { "No file $localFile, be sure to check it before calling" }
    val remoteFile = EelPathUtils.transferLocalContentToRemote(
      source = localFile,
      target = EelPathUtils.TransferTarget.Temporary(eel.descriptor)
    ).asEelPath().toString()
    _args.add(remoteFile)
  }
}