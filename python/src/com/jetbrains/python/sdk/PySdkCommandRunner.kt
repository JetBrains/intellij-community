// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.RunCanceledByUserException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.packaging.IndicatedProcessOutputListener
import com.jetbrains.python.packaging.PyExecutionException
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.pathString

internal object Logger {
  val LOG = logger<Logger>()
}

/**
 * Runs a command line operation in a background thread.
 *
 * @param [commandLine] The command line to execute.
 * @return A [Result] object containing the output of the command execution.
 */
@RequiresBackgroundThread
internal fun runCommandLine(commandLine: GeneralCommandLine): Result<String> {
  Logger.LOG.info("Running command: ${commandLine.commandLineString}")
  val commandOutput = with(CapturingProcessHandler(commandLine)) {
    runProcess()
  }

  return processOutput(
    commandOutput,
    commandLine.commandLineString,
    emptyList()
  )
}

fun runCommand(executable: Path, projectPath: Path?, @NlsContexts.DialogMessage errorMessage: String, vararg args: String): Result<String> {
  val command = listOf(executable.absolutePathString()) + args
  val commandLine = GeneralCommandLine(command).withWorkingDirectory(projectPath)
  val handler = CapturingProcessHandler(commandLine)
  val indicator = ProgressManager.getInstance().progressIndicator
  val result = with(handler) {
    when {
      indicator != null -> {
        addProcessListener(IndicatedProcessOutputListener(indicator))
        runProcessWithProgressIndicator(indicator)
      }
      else ->
        runProcess()
    }
  }

  return processOutput(result, executable.pathString, args.asList(), errorMessage)
}

/**
 * Processes the output of a command execution.
 *
 * @param[output] the output of the executed command.
 * @param[commandString]  the command string that was executed.
 * @param[args] the arguments passed to the command.
 * @param[errorMessage] the error message to be used if the command fails.
 * @return A [Result] object containing the processed output.
 */
internal fun processOutput(
  output: ProcessOutput,
  commandString: String,
  args: List<String>,
  @NlsContexts.DialogMessage errorMessage: String = "",
): Result<String> {
  return with(output) {
    when {
      isCancelled ->
        Result.failure(RunCanceledByUserException())
      exitCode != 0 ->
        Result.failure(PyExecutionException(errorMessage, commandString, args, stdout, stderr, exitCode, emptyList()))
      else -> Result.success(output.stdout.trim())
    }
  }
}