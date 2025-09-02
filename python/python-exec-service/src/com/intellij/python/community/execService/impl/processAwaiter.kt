// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.platform.eel.provider.utils.consumeAsInputStream
import com.intellij.python.community.execService.ProcessEvent
import com.intellij.python.community.execService.ProcessEvent.OutputType
import com.intellij.python.community.execService.ProcessEvent.OutputType.STDERR
import com.intellij.python.community.execService.ProcessEvent.OutputType.STDOUT
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.FlowCollector

/**
 * Awaits of process result and reports its stdout/stderr as a progress.
 */
internal suspend fun EelProcess.awaitWithReporting(progressListener: FlowCollector<ProcessEvent.ProcessOutput>?): EelProcessExecutionResult =
  coroutineScope {
    coroutineContext.job.invokeOnCompletion { err ->
      if (err != null) {
        GlobalScope.launch {
          this@awaitWithReporting.kill()
        }
      }
    }
    val stdout = async { report(STDOUT, progressListener) }
    val stderr = async { report(STDERR, progressListener) }
    EelProcessExecutionResult(exitCode.await(), stdout = stdout.await(), stderr = stderr.await())
  }

private suspend fun EelProcess.report(outputType: OutputType, to: FlowCollector<ProcessEvent.ProcessOutput>?): ByteArray = withContext(Dispatchers.IO) {
  val from = when (outputType) {
    STDOUT -> stdout
    STDERR -> stderr
  }
  val result = StringBuilder()
  from.consumeAsInputStream().bufferedReader().useLines { lines ->
    for (line in lines) {
      if (to != null && line.isNotBlank()) {
        to.emit(ProcessEvent.ProcessOutput(outputType, line))
      }
      result.appendLine(line)
    }
  }
  return@withContext result.toString().encodeToByteArray()
}