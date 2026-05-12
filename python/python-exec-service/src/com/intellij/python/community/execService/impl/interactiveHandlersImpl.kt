// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.platform.eel.provider.utils.asEelChannel
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.CustomErrorMessage
import com.intellij.python.community.execService.ProcessEvent
import com.intellij.python.community.execService.ProcessInteractiveHandler
import com.intellij.python.community.execService.ProcessSemiInteractiveFun
import com.intellij.python.community.execService.PyProcessListener
import com.jetbrains.python.Result
import com.jetbrains.python.mapError
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await

internal class ProcessSemiInteractiveHandlerImpl<T>(
  private val pyProcessListener: PyProcessListener?,
  private val code: ProcessSemiInteractiveFun<T>,
) : ProcessInteractiveHandler<T> {
  override suspend fun getResultFromProcess(binary: BinaryToExec, args: List<String>, process: Process): Result<T, Pair<EelProcessExecutionResult, CustomErrorMessage?>> =
    coroutineScope {
      pyProcessListener?.emit(ProcessEvent.ProcessStarted(binary, args))
      val processOutput = async { process.awaitWithReporting(pyProcessListener) }
      val result = code(process.outputStream.asEelChannel(), processOutput)
      // PY-89717: see processAwaiter.kt - and LoggingProcess.onExit() override
      // makes this resolve to ProcessHandleImpl.completion (the "process reaper"
      // path) rather than the FJP fallback in java.lang.Process.onExit().
      @Suppress("UsePlatformProcessAwaitExit")
      val exitCode = process.onExit().await().exitValue()
      pyProcessListener?.emit(ProcessEvent.ProcessEnded(exitCode))
      return@coroutineScope result.mapError { customErrorMessage ->
        Pair(processOutput.await(), customErrorMessage)
      }
    }
}
