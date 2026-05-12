// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.python.community.execService.ProcessEvent
import com.intellij.python.community.execService.ProcessEvent.OutputType
import com.intellij.python.community.execService.ProcessEvent.OutputType.STDERR
import com.intellij.python.community.execService.ProcessEvent.OutputType.STDOUT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

/**
 * Awaits of process result and reports its stdout/stderr as a progress.
 */
@ApiStatus.Internal
suspend fun Process.awaitWithReporting(progressListener: FlowCollector<ProcessEvent.ProcessOutput>?): EelProcessExecutionResult =
  coroutineScope {
    val stdout = async { report(STDOUT, progressListener) }
    val stderr = async { report(STDERR, progressListener) }
    // PY-89717: platform `awaitExit()` falls back to an unbounded
    // blocking dispatcher and parks a Dispatchers.IO worker in native
    // ProcessImpl.waitFor(), which ThreadLeakTracker reports as a leak when
    // the OS process is slow to exit (e.g. Docker target cleanup). Suspend on
    // ProcessHandle.onExit() instead — the wait is handled by the JDK's
    // "process reaper" daemon which is whitelisted by `ThreadLeakTracker`.
    @Suppress("UsePlatformProcessAwaitExit")
    val exitCode = onExit().await().exitValue()
    EelProcessExecutionResult(exitCode, stdout = stdout.await(), stderr = stderr.await())
  }

private suspend fun Process.report(outputType: OutputType, to: FlowCollector<ProcessEvent.ProcessOutput>?): ByteArray = withContext(Dispatchers.IO) {
  val from = when (outputType) {
    STDOUT -> inputStream
    STDERR -> errorStream
  }
  val result = StringBuilder()


  from.use { inputStream ->
    val reader = inputStream.reader()

    val currentLine = StringBuilder()
    while (true) {
      val char = try {
        reader.read() // Read might throw IOException if steam is closed explicitly in JVM, see test with the same commit
      }
      catch (e: IOException) {
        logger.warn("Error reading from process", e)
        -1
      }
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

private val logger = fileLogger()