// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.python.community.execService.impl.ExecServiceImpl
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ExecError
import com.jetbrains.python.errorProcessing.PyExecResult
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Error is an optional additionalMessage, that will be used instead of a default one for the [ExecError] in the [com.jetbrains.python.execution.PyExecutionFailure].
 */
typealias ProcessOutputTransformer<T> = (EelProcessExecutionResult) -> Result<T, @NlsSafe String?>

object ZeroCodeStdoutTransformer : ProcessOutputTransformer<String> {
  override fun invoke(processOutput: EelProcessExecutionResult): Result<String, String?> =
    if (processOutput.exitCode == 0) Result.success(processOutput.stdoutString.trim()) else Result.failure(null)
}

/**
 * Service is a thin wrapper over [EelApi] to execute python tools on local or remote Eel.
 * to obtain service, use function with same name.
 *
 * For all APIs but full interactive mode (which is a very low-level custom mode) stdout/stderr is reported as a progress.
 */
@ApiStatus.Internal
interface ExecService {

  /**
   * Execute code in a so-called "interactive" mode.
   * This is a quite advanced mode where *you* are responsible for converting a process to output.
   * You must listen for process stdout/stderr e.t.c.
   * Use it if you need to get some info from a process before it ends or to interact (i.e write into stdin).
   * See [ProcessInteractiveHandler] and [processSemiInteractiveHandler]
   */
  @CheckReturnValue
  suspend fun <T> executeInteractive(
    whatToExec: WhatToExec,
    args: List<String> = emptyList(),
    options: ExecOptions = ExecOptions(),
    processInteractiveHandler: ProcessInteractiveHandler<T>,
  ): PyExecResult<T>

  /**
   * Execute [whatToExec] with [args] and get both stdout/stderr outputs if `errorCode != 0`, returns error otherwise.
   * Function collects output lines and reports them to [procListener] if set
   *
   * @param[args] command line arguments
   * @param[options]  customizable process run options like timeout or environment variables to use
   * @return stdout or error. It is recommended to put this error into [com.jetbrains.python.errorProcessing.ErrorSink], but feel free to match and process it.
   */
  @CheckReturnValue
  suspend fun <T> execute(
    whatToExec: WhatToExec,
    args: List<String> = emptyList(),
    options: ExecOptions = ExecOptions(),
    procListener: PyProcessListener? = null,
    processOutputTransformer: ProcessOutputTransformer<T>,
  ): PyExecResult<T>

  /**
   * See [execute]
   */
  @CheckReturnValue
  suspend fun execGetStdout(
    whatToExec: WhatToExec,
    args: List<String> = emptyList(),
    options: ExecOptions = ExecOptions(),
    procListener: PyProcessListener? = null,
  ): PyExecResult<String> = execute(
    whatToExec = whatToExec,
    args = args,
    options = options,
    processOutputTransformer = ZeroCodeStdoutTransformer,
    procListener = procListener
  )
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
  val timeout: Duration = 1.minutes,
)

sealed interface WhatToExec {
  /**
   * [binary] (can reside on local or remote Eel, [EelApi] is calculated out of it)
   */
  data class Binary(val binary: Path) : WhatToExec {
    companion object {
      /**
       * Resolves relative name to the full name or `null` if [relativeBinName] can't be found in the path.
       */
      suspend fun fromRelativeName(eel: EelApi, relativeBinName: String): Binary? =
        eel.exec.findExeFilesInPath(relativeBinName).firstOrNull()?.let { Binary(it.asNioPath()) }
    }
  }

  /**
   * Execute [helper] on [python]. If [python] resides on remote Eel -- helper is copied there.
   * Note, that only **one** helper file is copied, not all helpers.
   */
  data class Helper(val python: PythonBinary, val helper: HelperName) : WhatToExec
}

/**
 * Default server implementation
 */
fun ExecService(): ExecService = ExecServiceImpl