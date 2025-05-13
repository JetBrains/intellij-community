// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.python.community.execService.*
import com.jetbrains.python.Result
import com.jetbrains.python.mapError
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal class ProcessSemiInteractiveHandlerImpl<T>(
  private val pyProcessListener: PyProcessListener?,
  private val code: ProcessSemiInteractiveFun<T>,
) : ProcessInteractiveHandler<T> {
  override suspend fun getResultFromProcess(whatToExec: WhatToExec, args: List<String>, process: EelProcess): Result<T, Pair<EelProcessExecutionResult, CustomErrorMessage?>> =
    coroutineScope {
      pyProcessListener?.emit(ProcessEvent.ProcessStarted(whatToExec, args))
      val processOutput = async { process.awaitWithReporting(pyProcessListener) }
      val result = code(process.stdin, process.exitCode)
      pyProcessListener?.emit(ProcessEvent.ProcessEnded(process.exitCode.await()))
      return@coroutineScope result.mapError { customErrorMessage ->
        Pair(processOutput.await(), customErrorMessage)
      }
    }
}
