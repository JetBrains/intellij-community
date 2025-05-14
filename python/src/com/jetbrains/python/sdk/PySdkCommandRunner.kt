// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.ProcessEvent
import com.intellij.python.community.execService.WhatToExec
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyExecResult
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path


/**
 * Executes a given executable with specified arguments within an optional project directory.
 * Progress is reported as `text` of the current progress (if any)
 *
 * @param [executable] The [Path] to the executable to run.
 * @param [workDir] The path to the project directory in which to run the executable, or null if no specific directory is required.
 * @param [args] The arguments to pass to the executable.
 * @return A [Result] object containing the output of the command execution.
 */
@Internal
suspend fun runExecutableWithProgress(executable: Path, workDir: Path?, vararg args: String): PyExecResult<String> {
  val ansiDecoder = AnsiEscapeDecoder()
  reportRawProgress { reporter ->
    return ExecService().execGetStdout(WhatToExec.Binary(executable), args.toList(), ExecOptions(workingDirectory = workDir), procListener = {
      when (it) {
        is ProcessEvent.ProcessStarted, is ProcessEvent.ProcessEnded -> Unit
        is ProcessEvent.ProcessOutput -> {
          val outType = when (it.stream) {
            ProcessEvent.OutputType.STDOUT -> ProcessOutputTypes.STDOUT
            ProcessEvent.OutputType.STDERR -> ProcessOutputTypes.STDERR
          }
          ansiDecoder.escapeText(it.line, outType) { text, attributes ->
            reporter.text(text)
          }
        }
      }
    })
  }
}
