// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.hatch

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.fs.EelFsError
import com.intellij.python.hatch.cli.HatchEnvironment
import com.intellij.python.hatch.service.CliBasedHatchService
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.basePath
import java.nio.file.Path

sealed class HatchError(message: @NlsSafe String) : MessageError(message)

class HatchExecutableNotFoundHatchError(path: Path?) : HatchError(
  PyHatchBundle.message("python.hatch.error.executable.is.not.found", path.toString())
)

class BasePythonExecutableNotFoundHatchError(pathString: String?) : HatchError(
  PyHatchBundle.message("python.hatch.error.base.python.executable.is.not.found", pathString.toString())
) {
  constructor(path: Path?) : this(path.toString())
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


data class HatchVirtualEnvironment(
  val hatchEnvironment: HatchEnvironment,
  val pythonVirtualEnvironment: PythonVirtualEnvironment?,
) {
  companion object {
    val AVAILABLE_ENVIRONMENTS_FOR_NEW_PROJECT: List<HatchVirtualEnvironment> = listOf(
      HatchVirtualEnvironment(HatchEnvironment.DEFAULT, null)
    )
  }
}

/**
 * Represents a Python virtual environment that can be either existing or non-existing.
 */
sealed interface PythonVirtualEnvironment {
  val pythonHomePath: PythonHomePath

  /**
   * Represents an existing Python virtual environment.
   * The environment was verified and the Python version was already discovered.
   */
  data class Existing(override val pythonHomePath: PythonHomePath, val pythonVersion: String) : PythonVirtualEnvironment

  /**
   * Represents a non-existing Python virtual environment.
   * This class is used for cases where the Python virtual environment is expected or referenced but does not exist on the file system.
   */
  data class NotExisting(override val pythonHomePath: PythonHomePath) : PythonVirtualEnvironment
}

data class ProjectStructure(
  val sourceRoot: Path?,
  val testRoot: Path?,
)

interface HatchService {
  fun getWorkingDirectoryPath(): Path

  suspend fun syncDependencies(envName: String): PyResult<String>

  suspend fun isHatchManagedProject(): PyResult<Boolean>

  suspend fun createNewProject(projectName: String): PyResult<ProjectStructure>

  /**
   * param[basePythonBinaryPath] base python for environment, the one on the PATH should be used if null.
   * param[envName] environment name to create, 'default' should be used if null.
   */
  suspend fun createVirtualEnvironment(basePythonBinaryPath: PythonBinary? = null, envName: String? = null): PyResult<PythonVirtualEnvironment.Existing>

  suspend fun findVirtualEnvironments(): PyResult<List<HatchVirtualEnvironment>>
}

/**
 * Hatch Service for working directory (where hatch.toml / pyproject.toml is usually placed)
 */
suspend fun Path.getHatchService(hatchExecutablePath: Path? = null): PyResult<HatchService> {
  return CliBasedHatchService(hatchExecutablePath = hatchExecutablePath, workingDirectoryPath = this)
}

/**
 * Hatch Service for Module.
 * Working directory considered as the module base path.
 */
suspend fun Module.getHatchService(hatchExecutablePath: Path? = null): PyResult<HatchService> {
  val workingDirectoryPath = resolveHatchWorkingDirectory(this.project, this).getOr { return it }
  return workingDirectoryPath.getHatchService(hatchExecutablePath = hatchExecutablePath)
}

/**
 * ../hatch/env/virtual/{normalized-project-name}/{hash}/{python-home}
 */
fun PythonHomePath.getHatchEnvVirtualProjectPath(): Path = this.parent.parent

fun resolveHatchWorkingDirectory(project: Project, module: Module?): PyResult<Path> {
  val pathString = module?.basePath ?: project.basePath

  return when (val path = pathString?.let { Path.of(it) }) {
    null -> Result.failure(WorkingDirectoryNotFoundHatchError(pathString))
    else -> Result.success(path)
  }
}