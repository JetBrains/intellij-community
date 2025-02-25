package com.intellij.python.hatch.service

import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.getOr
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.WhatToExec.Binary
import com.intellij.python.hatch.*
import com.intellij.python.hatch.cli.HatchEnvironmentType
import com.intellij.python.hatch.runtime.HatchRuntime
import com.intellij.python.hatch.runtime.createHatchRuntime
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.resolvePythonBinary
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

internal class CliBasedHatchService private constructor(
  private val workingDirectoryPath: Path,
  private val hatchRuntime: HatchRuntime,
) : HatchService {
  companion object {
    suspend operator fun invoke(workingDirectoryPath: Path, hatchExecutablePath: Path?): Result<CliBasedHatchService, PyError> {
      val hatchRuntime = createHatchRuntime(
        hatchExecutablePath=hatchExecutablePath,
        workingDirectoryPath=workingDirectoryPath,
      ).getOr { return it }
      return Result.success(CliBasedHatchService(workingDirectoryPath, hatchRuntime))
    }
  }

  override fun getWorkingDirectoryPath(): Path = workingDirectoryPath

  @RequiresBackgroundThread
  override suspend fun isHatchManagedProject(): Result<Boolean, PyError> {
    val isHatchManaged = when {
      workingDirectoryPath.resolve("hatch.toml").exists() -> true
      else -> {
        val pyProjectTomlPath = workingDirectoryPath.resolve("pyproject.toml").takeIf { it.isRegularFile() }
        val hatchRegex = """^\[tool\.hatch\..+]$""".toRegex(RegexOption.MULTILINE)
        pyProjectTomlPath?.readText()?.contains(hatchRegex) == true
      }
    }
    return Result.success(isHatchManaged)
  }

  @RequiresBackgroundThread
  override suspend fun findStandaloneEnvironments(): Result<List<HatchStandaloneEnvironment>, PyError> {
    val hatchEnv = hatchRuntime.hatchCli().env()
    val environments = hatchEnv.show().getOr { return it }
    val standaloneEnvironments = environments.getOrDefault(HatchEnvironmentType.STANDALONE, emptyList())

    val available = standaloneEnvironments.mapNotNull { env ->
      val pythonHomePath = hatchEnv.find(env.name).getOr { return@mapNotNull null } ?: return@mapNotNull null
      val pythonVirtualEnvironment = pythonHomePath.toPythonVirtualEnvironment().getOr { return@mapNotNull null }
      HatchStandaloneEnvironment(
        hatchEnvironment = env,
        pythonVirtualEnvironment = pythonVirtualEnvironment
      )
    }

    return Result.success(available)
  }

  @RequiresBackgroundThread
  override suspend fun createNewProject(projectName: String): Result<Unit, PyError> {
    val eelApi = workingDirectoryPath.getEelDescriptor().upgrade()
    val tempDir = eelApi.fs.createTemporaryDirectory(EelFileSystemApi.CreateTemporaryEntryOptions.Builder().build()).getOr { failure ->
      return Result.failure(FileSystemOperationHatchError(failure.error))
    }

    hatchRuntime.hatchCli().new(projectName, tempDir.asNioPath()).getOr { return it }
    eelApi.fs.move(
      tempDir,
      workingDirectoryPath.asEelPath(),
      EelFileSystemApi.ReplaceExistingDuringMove.DO_NOT_REPLACE_DIRECTORIES,
      true
    ).getOr { failure ->
      return Result.failure(FileSystemOperationHatchError(failure.error))
    }

    return Result.success(Unit)
  }

  @RequiresBackgroundThread
  override suspend fun createVirtualEnvironment(basePythonBinaryPath: PythonBinary?, envName: String?): Result<PythonVirtualEnvironment.Existing, PyError> {
    val pythonBasedRuntime = basePythonBinaryPath?.let { path ->
      hatchRuntime.withBasePythonBinaryPath(path).getOr { return it }
    } ?: hatchRuntime

    val hatchEnv = pythonBasedRuntime.hatchCli().env()

    hatchEnv.create(envName).getOr { return it }
    val pythonHomePath = hatchEnv.find(envName).getOr { return it }
    val pythonVirtualEnvironment = pythonHomePath?.toPythonVirtualEnvironment()?.getOr { return it }

    val result = when (pythonVirtualEnvironment) {
      is PythonVirtualEnvironment.Existing -> Result.success(pythonVirtualEnvironment)
      else -> Result.failure(EnvironmentCreationHatchError("Hatch didn't create environment but responded with ok"))
    }
    return result
  }
}

@RequiresBackgroundThread
internal suspend fun PythonHomePath.toPythonVirtualEnvironment(): Result<PythonVirtualEnvironment, PyError> {
  val pythonVersion = this.takeIf { it.isDirectory() }?.resolvePythonBinary()?.let { pythonBinaryPath ->
    ExecService().execGetStdout(Binary(pythonBinaryPath), listOf("--version")).getOr { return it }.trim()
  }
  val pythonVirtualEnvironment = when {
    pythonVersion == null -> PythonVirtualEnvironment.NotExisting(this)
    else -> PythonVirtualEnvironment.Existing(this, pythonVersion)
  }
  return Result.success(pythonVirtualEnvironment)
}