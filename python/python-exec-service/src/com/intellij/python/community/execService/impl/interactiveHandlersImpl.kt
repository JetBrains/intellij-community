// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.platform.eel.provider.utils.asEelChannel
import com.intellij.python.community.execService.*
import com.intellij.util.io.awaitExit
import com.jetbrains.python.Result
import com.jetbrains.python.mapError
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal class ProcessSemiInteractiveHandlerImpl<T>(
  private val pyProcessListener: PyProcessListener?,
  private val code: ProcessSemiInteractiveFun<T>,
) : ProcessInteractiveHandler<T> {
  override suspend fun getResultFromProcess(binary: BinaryToExec, args: List<String>, process: Process): Result<T, Pair<EelProcessExecutionResult, CustomErrorMessage?>> =
    coroutineScope {
      pyProcessListener?.emit(ProcessEvent.ProcessStarted(binary, args))
      val processOutput = async { process.awaitWithReporting(pyProcessListener) }
      val result = code(process.outputStream.asEelChannel(), processOutput)
      pyProcessListener?.emit(ProcessEvent.ProcessEnded(process.awaitExit()))
      return@coroutineScope result.mapError { customErrorMessage ->
        Pair(processOutput.await(), customErrorMessage)
      }
    }
}
