package com.intellij.python.hatch.runtime

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.execService.*
import com.intellij.python.community.execService.WhatToExec.Binary
import com.intellij.python.hatch.*
import com.intellij.python.hatch.cli.HatchCli
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyExecResult
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.resolvePythonBinary
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable

class HatchRuntime(
  val hatchBinary: Binary,
  val execOptions: ExecOptions,
  private val execService: ExecService = ExecService(),
) {
  fun hatchCli(): HatchCli = HatchCli(this)

  fun withEnv(vararg envVars: Pair<String, String>): HatchRuntime {
    return HatchRuntime(
      hatchBinary = this.hatchBinary,
      execOptions = this.execOptions.copy(env = this.execOptions.env + envVars)
    )
  }

  fun withWorkingDirectory(workDirectoryPath: Path): Result<HatchRuntime, HatchError> {
    if (!workDirectoryPath.isDirectory()) {
      return Result.failure(WorkingDirectoryNotFoundHatchError(workDirectoryPath))
    }

    val runtime = HatchRuntime(
      hatchBinary = this.hatchBinary,
      execOptions = this.execOptions.copy(workingDirectory = workDirectoryPath)
    )
    return Result.success(runtime)
  }

  fun withBasePythonBinaryPath(basePythonPath: PythonBinary): Result<HatchRuntime, HatchError> {
    if (!basePythonPath.isExecutable()) {
      return Result.failure(BasePythonExecutableNotFoundHatchError(basePythonPath))
    }

    val runtime = withEnv(HatchConstants.AppEnvVars.PYTHON to basePythonPath.toString())
    return Result.success(runtime)
  }

  /**
   * Pure execution of [hatchBinary] with command line [arguments] and [execOptions] by [execService]
   * Doesn't make any validation of stdout/stderr content.
   */
  internal suspend fun <T> execute(vararg arguments: String, processOutputTransformer: ProcessOutputTransformer<T>): PyExecResult<T> {
    return execService.execute(hatchBinary, arguments.toList(), execOptions, processOutputTransformer = processOutputTransformer)
  }

  internal suspend fun <T> executeInteractive(vararg arguments: String, processSemiInteractiveFun: ProcessSemiInteractiveFun<T>): PyExecResult<T> {
    return execService.executeInteractive(hatchBinary, arguments.toList(), execOptions, processSemiInteractiveHandler(code = processSemiInteractiveFun))
  }

  internal suspend fun resolvePythonVirtualEnvironment(pythonHomePath: PythonHomePath): PyResult<PythonVirtualEnvironment> {
    val pythonVersion = pythonHomePath.takeIf { it.isDirectory() }?.resolvePythonBinary()?.let { pythonBinaryPath ->
      execService.execGetStdout(Binary(pythonBinaryPath), listOf("--version")).getOr { return it }.trim()
    }
    val pythonVirtualEnvironment = when {
      pythonVersion == null -> PythonVirtualEnvironment.NotExisting(pythonHomePath)
      else -> PythonVirtualEnvironment.Existing(pythonHomePath, pythonVersion)
    }
    return Result.success(pythonVirtualEnvironment)
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
    hatchBinary = Binary(actualHatchExecutable),
    execOptions = ExecOptions(
      env = actualEnvVars,
      workingDirectory = workingDirectoryPath
    )
  )
  return Result.success(runtime)
}
