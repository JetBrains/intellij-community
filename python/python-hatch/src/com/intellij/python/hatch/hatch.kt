// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.hatch

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.fs.EelFsError
import com.intellij.python.hatch.cli.HatchEnvironment
import com.intellij.python.hatch.service.CliBasedHatchService
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.sdk.basePath
import java.nio.file.Path

sealed class HatchError(message: @NlsSafe String) : PyError.Message(message)

class HatchExecutableNotFoundHatchError(path: Path?) : HatchError(
  PyHatchBundle.message("python.hatch.error.executable.is.not.found", path.toString())
)

class BasePythonExecutableNotFoundHatchError(pathString: String?) : HatchError(
  PyHatchBundle.message("python.hatch.error.base.python.executable.is.not.found", pathString.toString())
) {
  constructor(path: Path) : this(path.toString())
}

class WorkingDirectoryNotFoundHatchError(pathString: String?) : HatchError(
  PyHatchBundle.message("python.hatch.error.working.directory.is.not.found", pathString.toString())
) {
  constructor(path: Path?) : this(path.toString())
}

class EnvironmentCreationHatchError(details: @NlsSafe String) : HatchError(
  PyHatchBundle.message("python.hatch.error.environment.creation", details)
)

class FileSystemOperationHatchError(eelFsError: EelFsError) : HatchError(
  PyHatchBundle.message("python.hatch.error.filesystem.operation", eelFsError)
)


data class HatchStandaloneEnvironment(
  val hatchEnvironment: HatchEnvironment,
  val pythonVirtualEnvironment: PythonVirtualEnvironment,
) {
  companion object {
    val AVAILABLE_ENVIRONMENTS_FOR_NEW_PROJECT: List<HatchStandaloneEnvironment> = listOf(
      HatchStandaloneEnvironment(HatchEnvironment.DEFAULT, PythonVirtualEnvironment.NotExisting())
    )
  }
}

sealed interface PythonVirtualEnvironment {
  val pythonHomePath: PythonHomePath?

  data class Existing(override val pythonHomePath: PythonHomePath, val pythonVersion: String) : PythonVirtualEnvironment
  data class NotExisting(override val pythonHomePath: PythonHomePath? = null) : PythonVirtualEnvironment
}

interface HatchService {
  fun getWorkingDirectoryPath(): Path

  @RequiresBackgroundThread
  suspend fun isHatchManagedProject(): Result<Boolean, PyError>

  @RequiresBackgroundThread
  suspend fun createNewProject(projectName: String): Result<Unit, PyError>

  /**
   * param[basePythonBinaryPath] base python for environment, the one on the PATH should be used if null.
   * param[envName] environment name to create, 'default' should be used if null.
   */
  @RequiresBackgroundThread
  suspend fun createVirtualEnvironment(basePythonBinaryPath: PythonBinary? = null, envName: String? = null): Result<PythonVirtualEnvironment.Existing, PyError>

  @RequiresBackgroundThread
  suspend fun findStandaloneEnvironments(): Result<List<HatchStandaloneEnvironment>, PyError>
}

/**
 * Hatch Service for working directory (where hatch.toml / pyproject.toml is usually placed)
 */
@RequiresBackgroundThread
suspend fun getHatchService(workingDirectoryPath: Path, hatchExecutablePath: Path? = null): Result<HatchService, PyError> {
  return CliBasedHatchService(hatchExecutablePath = hatchExecutablePath, workingDirectoryPath = workingDirectoryPath)
}

/**
 * Hatch Service for Module.
 * Working directory considered as the module base path.
 */
@RequiresBackgroundThread
suspend fun Module.getHatchService(hatchExecutablePath: Path? = null): Result<HatchService, PyError> {
  val workingDirectoryPath = basePath?.let { Path.of(it) }
                             ?: return Result.failure(WorkingDirectoryNotFoundHatchError(basePath))
  return getHatchService(workingDirectoryPath = workingDirectoryPath, hatchExecutablePath = hatchExecutablePath)
}

/**
 * ../hatch/env/virtual/{normalized-project-name}/{hash}/{python-home}
 */
fun PythonHomePath.getHatchEnvVirtualProjectPath(): Path = this.parent.parent
