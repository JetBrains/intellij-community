// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.hatch

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.execService.UploadConfig
import com.intellij.python.hatch.cli.HatchEnvironment
import com.intellij.python.hatch.service.CliBasedHatchService
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.baseDir
import java.nio.file.Path

const val HATCH_TOML: String = "hatch.toml"

sealed class HatchError(message: @NlsSafe String) : MessageError(message)

class HatchExecutableNotFoundHatchError(path: Path?) : HatchError(
  PyHatchBundle.message("python.hatch.error.executable.is.not.found", path.toString())
)

class BasePythonExecutableNotFoundHatchError(pathString: String?) : HatchError(
  PyHatchBundle.message("python.hatch.error.base.python.executable.is.not.found", pathString.toString())
) {
  constructor(path: Path?) : this(path.toString())
}

class EnvironmentCreationHatchError(details: @NlsSafe String) : HatchError(
  PyHatchBundle.message("python.hatch.error.environment.creation", details)
)

class FileSystemOperationHatchError(details: @NlsSafe Any) : HatchError(
  PyHatchBundle.message("python.hatch.error.filesystem.operation", details)
)

class WorkingDirectoryNotFoundHatchError(pathString: String?) : HatchError(
  PyHatchBundle.message("python.hatch.error.working.directory.not.found", pathString.toString())
) {
  constructor(path: Path?) : this(path?.toString())
}

data class HatchVirtualEnvironment<P : PathHolder>(
  val hatchEnvironment: HatchEnvironment,
  val pythonVirtualEnvironment: PythonVirtualEnvironment<P>?,
) {
  companion object {
    fun <P : PathHolder> availableEnvironmentsForNewProject(): List<HatchVirtualEnvironment<P>> = listOf(
      HatchVirtualEnvironment(HatchEnvironment.DEFAULT, null)
    )
  }
}

/**
 * Represents a Python virtual environment that can be either existing or non-existing.
 */
sealed interface PythonVirtualEnvironment<P : PathHolder> {
  val pythonHomePath: P

  /**
   * Represents an existing Python virtual environment.
   * The environment was verified and the Python version was already discovered.
   */
  data class Existing<P : PathHolder>(override val pythonHomePath: P, val pythonInfo: PythonInfo) : PythonVirtualEnvironment<P>

  /**
   * Represents a non-existing Python virtual environment.
   * This class is used for cases where the Python virtual environment is expected or referenced but does not exist on the file system.
   */
  data class NotExisting<P : PathHolder>(override val pythonHomePath: P) : PythonVirtualEnvironment<P>
}

data class ProjectStructure(
  val sourceRoot: Path?,
  val testRoot: Path?,
)

interface HatchService<P : PathHolder> {
  fun getWorkingDirectoryPath(): Path

  suspend fun syncDependencies(envName: String? = null): PyResult<String>

  suspend fun isHatchManagedProject(): Boolean

  /**
   * param[basePythonBinaryPath] base python for environment, the one on the PATH should be used if null.
   * param[envName] environment name to create, 'default' should be used if null.
   */
  suspend fun createVirtualEnvironment(
    basePythonBinaryPath: P? = null,
    envName: String? = null,
  ): PyResult<PythonVirtualEnvironment.Existing<P>>

  suspend fun findVirtualEnvironments(): PyResult<List<HatchVirtualEnvironment<P>>>

  /**
   * This function detects all Hatch virtual environments and returns the 'default' one if it exists. If such an environment
   * doesn't exist, `null` is returned. In case of errors `PyError` is returned.
   */
  suspend fun findDefaultVirtualEnvironmentOrNull(): PyResult<HatchVirtualEnvironment<P>?>
}

interface HatchProjectStructureService : HatchService<PathHolder.Eel> {
  suspend fun createNewProjectLocally(projectName: String): PyResult<ProjectStructure>
}

/**
 * Hatch Service for working directory (where hatch.toml / pyproject.toml is usually placed)
 */
suspend fun <P : PathHolder> Path?.getHatchService(
  fileSystem: FileSystem<P>,
  hatchExecutablePath: P? = null,
  hatchEnvironmentName: String? = null,
  uploadBeforeExecution: UploadConfig? = null,
): PyResult<HatchService<P>> {
  return CliBasedHatchService(
    fileSystem = fileSystem,
    hatchExecutablePath = hatchExecutablePath,
    workingDirectoryPath = this,
    hatchEnvironmentName = hatchEnvironmentName,
    uploadBeforeExecution = uploadBeforeExecution,
  )
}

/**
 * Hatch Service for Module.
 * Working directory considered as the module base path.
 */
suspend fun Module.getHatchService(
  fileSystem: FileSystem<PathHolder.Eel>,
  hatchExecutablePath: Path? = null,
  uploadBeforeExecution: UploadConfig? = null,
): PyResult<HatchProjectStructureService> {
  val workingDirectoryPath = resolveHatchWorkingDirectory(this.project, this).getOr { return it }
  return CliBasedHatchService.createProjectStructureService(
    fileSystem = fileSystem,
    hatchExecutablePath = hatchExecutablePath?.let { PathHolder.Eel(it) },
    workingDirectoryPath = workingDirectoryPath,
    uploadBeforeExecution = uploadBeforeExecution,
  )
}

fun resolveHatchWorkingDirectory(project: Project, module: Module?): PyResult<Path> {
  val pathString = module?.baseDir?.path ?: project.basePath

  return when (val path = pathString?.let { Path.of(it) }) {
    null -> Result.failure(WorkingDirectoryNotFoundHatchError(pathString))
    else -> Result.success(path)
  }
}