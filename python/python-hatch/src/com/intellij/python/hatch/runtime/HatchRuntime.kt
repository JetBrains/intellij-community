package com.intellij.python.hatch.runtime

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.execService.*
import com.intellij.python.hatch.BasePythonExecutableNotFoundHatchError
import com.intellij.python.hatch.HatchConfiguration
import com.intellij.python.hatch.HatchError
import com.intellij.python.hatch.WorkingDirectoryNotFoundHatchError
import com.intellij.python.hatch.cli.HatchCli
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable

class HatchRuntime(
  val hatchBinary: WhatToExec.Binary,
  val execOptions: ExecOptions,
  private val execService: ExecService = ExecService(),
) {
  fun hatchCli(): HatchCli = HatchCli(this)

  fun withWorkingDirectory(workDirectoryPath: Path): Result<HatchRuntime, HatchError> {
    if (!workDirectoryPath.isDirectory()) {
      return Result.failure(WorkingDirectoryNotFoundHatchError(workDirectoryPath))
    }

    val execOptions = with(this.execOptions) {
      ExecOptions(env, workDirectoryPath, processDescription, timeout)
    }
    return Result.success(HatchRuntime(this.hatchBinary, execOptions))
  }

  fun withBasePythonBinaryPath(basePythonPath: PythonBinary): Result<HatchRuntime, HatchError> {
    if (!basePythonPath.isExecutable()) {
      return Result.failure(BasePythonExecutableNotFoundHatchError(basePythonPath))
    }

    val execOptions = with(this.execOptions) {
      val modifiedEnv = env + mapOf(
        HatchConstants.AppEnvVars.PYTHON to basePythonPath.toString()
      )
      ExecOptions(modifiedEnv, workingDirectory, processDescription, timeout)
    }
    return Result.success(HatchRuntime(this.hatchBinary, execOptions))
  }

  /**
   * Pure execution of [hatchBinary] with command line [arguments] and [execOptions] by [execService]
   * Doesn't make any validation of stdout/stderr content.
   */
  internal suspend fun <T> execute(vararg arguments: String, processOutputTransformer: ProcessOutputTransformer<T>): Result<T, PyError.ExecException> {
    return execService.execute(hatchBinary, arguments.toList(), execOptions, processOutputTransformer)
  }

  internal suspend fun <T> executeInteractive(vararg arguments: String, eelProcessInteractiveHandler: EelProcessInteractiveHandler<T>): Result<T, PyError.ExecException> {
    return execService.executeInteractive(hatchBinary, arguments.toList(), execOptions, eelProcessInteractiveHandler)
  }
}


suspend fun createHatchRuntime(
  hatchExecutablePath: Path?,
  workingDirectoryPath: Path?,
  envVars: Map<String, String> = emptyMap(),
  eelApi: EelApi = localEel,
): Result<HatchRuntime, HatchError> {
  val actualHatchExecutable = hatchExecutablePath
                              ?: HatchConfiguration.getOrDetectHatchExecutablePath(eelApi).getOr { return it }
  if (workingDirectoryPath?.isDirectory() != true) {
    return Result.failure(WorkingDirectoryNotFoundHatchError(workingDirectoryPath))
  }

  val defaultVariables = mapOf(
    HatchConstants.AppEnvVars.NO_COLOR to "1",
    HatchConstants.AppEnvVars.VERBOSE to "1",
    HatchConstants.AppEnvVars.INTERACTIVE to "0",
    "TERM" to "dumb",
    "COLUMNS" to "${0x7FFF}",
    "LINES" to "${0x7FFF}",
  )
  val actualEnvVars = defaultVariables + envVars

  val runtime = HatchRuntime(
    hatchBinary = WhatToExec.Binary(actualHatchExecutable),
    execOptions = ExecOptions(
      env = actualEnvVars,
      workingDirectory = workingDirectoryPath
    )
  )
  return Result.success(runtime)
}
