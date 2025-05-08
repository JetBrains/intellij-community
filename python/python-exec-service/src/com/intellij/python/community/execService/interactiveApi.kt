// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService

import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.jetbrains.python.Result
import com.jetbrains.python.mapError
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.Nls
import java.io.IOException


/**
 * Message to be displayed to a user in case of process failure.
 */
typealias CustomErrorMessage = @Nls String


/**
 * In most cases you need [ProcessSemiInteractiveHandler]
 */
fun interface ProcessInteractiveHandler<T> {
  /**
   * Reads output from [process], decides if success or not.
   * In latter case returns [EelProcessExecutionResult] (created out of collected output) and optional error message.
   * If no message returned -- the default one is used.
   */
  suspend fun getResultFromProcess(process: EelProcess): Result<T, Pair<EelProcessExecutionResult, CustomErrorMessage?>>
}


/**
 * [ProcessInteractiveHandler], but you do not have to collect output by yourself. You only have access to stdout and exit code.
 * So, you can only *write* something to process.
 */
class ProcessSemiInteractiveHandler<T>(private val code: ProcessSemiInteractiveFun<T>) : ProcessInteractiveHandler<T> {
  override suspend fun getResultFromProcess(process: EelProcess): Result<T, Pair<EelProcessExecutionResult, CustomErrorMessage?>> {
    val result = code(process.stdin, process.exitCode)
    val processOutput = process.awaitProcessResult()
    return result.mapError { customErrorMessage ->
      Pair(processOutput, customErrorMessage)
    }
  }
}

/**
 * Process stdout -> result
 */
typealias ProcessSemiInteractiveFun<T> = suspend (EelSendChannel<IOException>, Deferred<Int>) -> Result<T, CustomErrorMessage?>
