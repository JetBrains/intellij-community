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
import kotlin.time.Duration.Companion.seconds

/**
 * Awaits of process result and reports its stdout/stderr as a progress.
 */
internal suspend fun EelProcess.awaitWithReporting(progressListener: FlowCollector<ProcessEvent.ProcessOutput>?): EelProcessExecutionResult =
  coroutineScope {
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


  from.consumeAsInputStream().use { inputStream ->
    val reader = inputStream.reader()

    val currentLine = StringBuilder()
    while (true) {
      val char = reader.read()
      if (char == -1) break // EOF

      when (val c = char.toChar()) {
        '\b' -> {
          if (to != null && currentLine.isNotBlank()) {
            to.emit(ProcessEvent.ProcessOutput(outputType, currentLine.toString()))
          }

          // Backspace - remove last character
          if (currentLine.isNotEmpty()) {
            currentLine.deleteCharAt(currentLine.length - 1)
          }
        }
        '\r' -> {
          //ignore
        }
        '\n' -> {
          // Line feed - emit final line and add to result
          val finalLine = currentLine.toString()
          result.appendLine(finalLine)
          if (to != null && finalLine.isNotEmpty()) {
            to.emit(ProcessEvent.ProcessOutput(outputType, finalLine))
          }
          currentLine.clear()
        }
        else -> {
          // Regular character
          currentLine.append(c)
        }
      }
    }
    result.append(currentLine)
  }
  return@withContext result.toString().encodeToByteArray()
}