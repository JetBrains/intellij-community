// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.RunCanceledByUserException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsContexts
import com.jetbrains.python.packaging.PyExecutionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import kotlin.io.path.absolutePathString

internal object Logger {
  val LOG = logger<Logger>()
}

/**
 * Runs a command line operation in a background thread.
 *
 * @param [commandLine] The command line to execute.
 * @return A [Result] object containing the output of the command execution.
 */
internal suspend fun runCommandLine(commandLine: GeneralCommandLine): Result<String> {
  Logger.LOG.info("Running command: ${commandLine.commandLineString}")
  val commandOutput = with(CapturingProcessHandler(commandLine)) {
    withContext(Dispatchers.IO) {
      runProcess()
    }
  }

  return processOutput(
    commandOutput,
    commandLine.commandLineString,
    commandLine.parametersList.list,
  )
}

/**
 * Executes a given executable with specified arguments within an optional project directory.
 *
 * @param [executable] The [Path] to the executable to run.
 * @param [projectPath] The path to the project directory in which to run the executable, or null if no specific directory is required.
 * @param [args] The arguments to pass to the executable.
 * @return A [Result] object containing the output of the command execution.
 */
@Internal
suspend fun runExecutable(executable: Path, projectPath: Path?, vararg args: String): Result<String> {
  val commandLine = GeneralCommandLine(listOf(executable.absolutePathString()) + args).withWorkingDirectory(projectPath)
  return runCommandLine(commandLine)
}

/**
 * Executes a specified [command] within the given project path with optional arguments.
 *
 * @param [projectPath] the path to the project directory where the command should be executed
 * @param [command] the command to be executed
 * @param [args] optional arguments for the command
 * @return a [Result] object containing the output of the command execution
 */
@Internal
suspend fun runCommand(projectPath: Path, command: String, vararg args: String): Result<String> {
  val commandLine = GeneralCommandLine(listOf(command) + args).withWorkingDirectory(projectPath)
  return runCommandLine(commandLine)
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