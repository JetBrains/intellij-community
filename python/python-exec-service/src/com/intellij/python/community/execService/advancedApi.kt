// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService

import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.python.community.execService.impl.ProcessSemiInteractiveHandlerImpl
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue
import org.jetbrains.annotations.Nls


// This is an advanced API, consider using basic api.kt


/**
 * Service is a thin wrapper over [com.intellij.platform.eel.EelApi] to execute python tools on local or remote Eel.
 * to obtain service, use function with same name.
 *
 * For all APIs but full interactive mode (which is a very low-level custom mode) stdout/stderr is reported as progress.
 */
@ApiStatus.Internal
interface ExecService {

  /**
   * TL;TR: Use extension functions from `api.kt`, do not use it directly!
   *
   * Execute code in a so-called "interactive" mode.
   * This is a quite advanced mode where *you* are responsible for converting a process to output.
   * You must listen for process stdout/stderr e.t.c.
   * Use it if you need to get some info from a process before it ends or to interact (i.e. write into stdin).
   * See [ProcessInteractiveHandler] and [processSemiInteractiveHandler].
   */
  @CheckReturnValue
  suspend fun <T> executeAdvanced(
    binary: BinaryToExec,
    args: Args,
    options: ExecOptions = ExecOptions(),
    processInteractiveHandler: ProcessInteractiveHandler<T>,
  ): PyResult<T>
}

/**
 * Message to be displayed to a user in case of process failure.
 */
typealias CustomErrorMessage = @Nls String


/**
 * In most cases you need [processSemiInteractiveHandler]
 */
fun interface ProcessInteractiveHandler<T> {
  /**
   * Reads output from [process], decides if success or not.
   * In latter case returns [EelProcessExecutionResult] (created out of collected output) and optional error message.
   * If no message returned -- the default one is used.
   */
  suspend fun getResultFromProcess(binary: BinaryToExec, args: List<String>, process: Process): Result<T, Pair<EelProcessExecutionResult, CustomErrorMessage?>>
}


/**
 * Process stdout -> result
 */
typealias ProcessSemiInteractiveFun<T> = suspend (EelSendChannel, Deferred<EelProcessExecutionResult>) -> Result<T, CustomErrorMessage?>

/**
 * [ProcessInteractiveHandler], but you do not have to collect output by yourself. You only have access to stdout and exit code.
 * Function collects output lines and reports them to [pyProcessListener] if set
 * So, you can only *write* something to process.
 */
fun <T> processSemiInteractiveHandler(pyProcessListener: PyProcessListener? = null, code: ProcessSemiInteractiveFun<T>): ProcessInteractiveHandler<T> = ProcessSemiInteractiveHandlerImpl(pyProcessListener, code)
