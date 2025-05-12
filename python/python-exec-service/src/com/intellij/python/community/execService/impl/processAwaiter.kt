// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.platform.eel.provider.utils.consumeAsInputStream
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.platform.util.progress.reportRawProgress
import kotlinx.coroutines.*
import java.io.IOException

/**
 * Awaits of process result and reports its stdout/stderr as a progress.
 */
internal suspend fun EelProcess.awaitWithReporting(): EelProcessExecutionResult =
  coroutineScope {
    reportRawProgress { reporter ->
      coroutineContext.job.invokeOnCompletion { err ->
        if (err != null) {
          GlobalScope.launch {
            this@awaitWithReporting.kill()
          }
        }
      }
      val stdout = async { report(stdout, reporter) }
      val stderr = async { report(stderr, reporter) }
      EelProcessExecutionResult(exitCode.await(), stdout = stdout.await(), stderr = stderr.await())

    }
  }

private suspend fun report(from: EelReceiveChannel<IOException>, to: RawProgressReporter): ByteArray = withContext(Dispatchers.IO) {
  val result = StringBuilder()
  from.consumeAsInputStream().bufferedReader().useLines { lines ->
    for (line in lines) {
      if (line.isNotBlank()) {
        to.details(line)
      }
      result.appendLine(line)
    }
  }
  return@withContext result.toString().encodeToByteArray()
}