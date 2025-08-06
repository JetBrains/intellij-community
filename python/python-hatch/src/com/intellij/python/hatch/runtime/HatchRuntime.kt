package com.intellij.python.hatch.runtime

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.execService.*
import com.intellij.python.hatch.*
import com.intellij.python.hatch.cli.HatchCli
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.resolvePythonBinary
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.time.Duration.Companion.minutes

class HatchRuntime(
  val hatchBinary: BinOnEel,
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
      hatchBinary = this.hatchBinary.copy(workDir = workDirectoryPath),
      execOptions = this.execOptions
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
  internal suspend fun <T> execute(vararg arguments: String, processOutputTransformer: ProcessOutputTransformer<T>): PyResult<T> {
    return execService.execute(hatchBinary, Args(*arguments), execOptions, processOutputTransformer = processOutputTransformer)
  }

  internal suspend fun <T> executeInteractive(vararg arguments: String, processSemiInteractiveFun: ProcessSemiInteractiveFun<T>): PyResult<T> {
    return execService.executeAdvanced(hatchBinary, Args(*arguments), execOptions, processSemiInteractiveHandler(code = processSemiInteractiveFun))
  }

  internal suspend fun resolvePythonVirtualEnvironment(pythonHomePath: PythonHomePath): PyResult<PythonVirtualEnvironment> {
    val pythonVersion = pythonHomePath.takeIf { it.isDirectory() }?.resolvePythonBinary()?.let { pythonBinaryPath ->
      execService.execGetStdout(pythonBinaryPath, Args("--version"),
                                ExecOptions(timeout = 20.minutes),
                                procListener = null).getOr { return it }.trim()
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
    hatchBinary = BinOnEel(actualHatchExecutable, workingDirectoryPath),
    execOptions = ExecOptions(
      env = actualEnvVars,
    )
  )
  return Result.success(runtime)
}
