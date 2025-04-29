// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelProcess
import com.intellij.python.community.execService.impl.ExecServiceImpl
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ExecError
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Error is an optional additionalMessage, that will be used instead of a default one for the [ExecError] in the [com.jetbrains.python.execution.PyExecutionFailure].
 */
typealias ProcessOutputTransformer<T> = (ProcessOutput) -> Result<T, @NlsSafe String?>

typealias EelProcessInteractiveHandler<T> = suspend (EelProcess) -> Result<T, @NlsSafe String?>

object ZeroCodeStdoutTransformer : ProcessOutputTransformer<String> {
  override fun invoke(processOutput: ProcessOutput): Result<String, String?> =
    if (processOutput.exitCode == 0) Result.success(processOutput.stdout.trim()) else Result.failure(null)
}

/**
 * Service is a thin wrapper over [EelApi] to execute python tools on local or remote Eel.
 * to obtain service, use function with same name.
 */
@ApiStatus.Internal
interface ExecService {

  @CheckReturnValue
  suspend fun <T> executeInteractive(
    whatToExec: WhatToExec,
    args: List<String> = emptyList(),
    options: ExecOptions = ExecOptions(),
    eelProcessInteractiveHandler: EelProcessInteractiveHandler<T>,
  ): Result<T, ExecError>

  /**
   * Execute [whatToExec] with [args] and get both stdout/stderr outputs if `errorCode != 0`, gets error otherwise.
   * If you want to show a modal window with progress, use `withModalProgress`.
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
    processOutputTransformer: ProcessOutputTransformer<T>,
  ): Result<T, ExecError>

  @CheckReturnValue
  suspend fun execGetStdout(
    whatToExec: WhatToExec,
    args: List<String> = emptyList(),
    options: ExecOptions = ExecOptions(),
  ): Result<String, ExecError> = execute(
    whatToExec = whatToExec,
    args = args,
    options = options,
    processOutputTransformer = ZeroCodeStdoutTransformer
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
  data class Binary(val binary: Path) : WhatToExec

  /**
   * Execute [helper] on [python]. If [python] resides on remote Eel -- helper is copied there.
   * Note, that only **one** helper file is copied, not all helpers.
   */
  data class Helper(val python: PythonBinary, val helper: HelperName) : WhatToExec

  /**
   * Random command on [eel]. [EelApi] will look for it in the path
   */
  data class Command(val eel: EelApi, val command: String) : WhatToExec
}

/**
 * Default server implementation
 */
fun ExecService(): ExecService = ExecServiceImpl