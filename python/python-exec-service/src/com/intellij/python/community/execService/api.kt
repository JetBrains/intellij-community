// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService

import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.getShell
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.python.community.execService.impl.ExecServiceImpl
import com.intellij.python.community.execService.impl.PyExecBundle
import com.intellij.python.community.execService.impl.transformerToHandler
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ExecError
import com.jetbrains.python.errorProcessing.PyResult
import org.jetbrains.annotations.CheckReturnValue
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes


/**
 * Default service implementation
 */
fun ExecService(): ExecService = ExecServiceImpl


/**
 * Execute [binary] right directly on the eel it resides on.
 */
suspend fun ExecService.execGetStdout(
  binary: Path,
  args: List<String> = emptyList(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
): PyResult<String> = execute(
  binary = binary,
  args = args,
  options = options,
  processOutputTransformer = ZeroCodeStdoutTransformer,
  procListener = procListener
)


/**
 * Execute [binaryName] on [eelApi].
 * This [binaryName] will be searched in `PATH`
 */
suspend fun ExecService.execGetStdout(
  eelApi: EelApi,
  binaryName: String,
  args: List<String> = emptyList(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
): PyResult<String> {
  val binary = eelApi.exec.findExeFilesInPath(binaryName).firstOrNull()?.asNioPath()
               ?: return PyResult.localizedError(PyExecBundle.message("py.exec.fileNotFound", binaryName, eelApi.descriptor.machine.name))
  return execGetStdout(binary, args, options, procListener)
}


/**
 * Execute [commandForShell] on [eelApi].
 * Shell is `cmd` for Windows and Bourne Shell for POSIX.
 */
suspend fun ExecService.execGetStdoutInShell(
  eelApi: EelApi,
  commandForShell: String,
  args: List<String> = emptyList(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
): PyResult<String> {
  val (shell, arg) = eelApi.exec.getShell()
  return execGetStdout(shell.asNioPath(), listOf(arg, commandForShell) + args, options, procListener)
}

/**
 * Execute [binary] with [args] and get both stdout/stderr outputs if `errorCode != 0`, returns error otherwise.
 * Function collects output lines and reports them to [procListener] if set
 *
 * @param[args] command line arguments
 * @param[options]  customizable process run options like timeout or environment variables to use
 * @return stdout or error. It is recommended to put this error into [com.jetbrains.python.errorProcessing.ErrorSink], but feel free to match and process it.
 */
@CheckReturnValue
suspend fun <T> ExecService.execute(
  binary: Path,
  args: List<String> = emptyList(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
  processOutputTransformer: ProcessOutputTransformer<T>,
): PyResult<T> {
  return reportRawProgress { reporter ->
    val ansiDecoder = AnsiEscapeDecoder()
    val listener = procListener ?: PyProcessListener {
      when (it) {
        is ProcessEvent.ProcessStarted, is ProcessEvent.ProcessEnded -> Unit
        is ProcessEvent.ProcessOutput -> {
          val outType = when (it.stream) {
            ProcessEvent.OutputType.STDOUT -> ProcessOutputTypes.STDOUT
            ProcessEvent.OutputType.STDERR -> ProcessOutputTypes.STDERR
          }
          ansiDecoder.escapeText(it.line, outType) { text, _ ->
            @Suppress("HardCodedStringLiteral")
            reporter.text(text)
          }
        }
      }
    }
    executeAdvanced(binary, { addArgs(*args.toTypedArray()) }, options, transformerToHandler(procListener
                                                                                             ?: listener, processOutputTransformer))
  }

}


/**
 * Error is an optional additionalMessage, that will be used instead of a default one for the [ExecError]
 */
typealias ProcessOutputTransformer<T> = (EelProcessExecutionResult) -> Result<T, @NlsSafe String?>

object ZeroCodeStdoutTransformer : ProcessOutputTransformer<String> {
  override fun invoke(processOutput: EelProcessExecutionResult): Result<String, String?> =
    if (processOutput.exitCode == 0) Result.success(processOutput.stdoutString.trim()) else Result.failure(null)
}


/**
 * @property[workingDirectory] Directory where to run the process (PWD)
 * @property[env] Environment variables to be applied with the process run
 * @property[timeout] Process gets killed after this timeout
 * @property[processDescription] optional description to be displayed to user
 */
data class ExecOptions(
  val env: Map<String, String> = emptyMap(),
  val workingDirectory: Path? = null,
  val processDescription: @Nls String? = null,
  val timeout: Duration = 5.minutes,
)
