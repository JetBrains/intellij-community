// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.runExecutable
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.SystemDependent
import java.io.FileNotFoundException
import java.nio.file.Path

@Internal
suspend fun runPipEnv(dirPath: Path?, vararg args: String): Result<String> {
  val executable = getPipEnvExecutable().getOrElse { return Result.failure(it) }
  return runExecutable(executable, dirPath, *args)
}

/**
 * The user-set persisted a path to the pipenv executable.
 */
var PropertiesComponent.pipEnvPath: @SystemDependent String?
  get() = getValue(PIPENV_PATH_SETTING)
  set(value) {
    setValue(PIPENV_PATH_SETTING, value)
  }

/**
 * Detects the pipenv executable in `$PATH`.
 */
@Internal
suspend fun detectPipEnvExecutable(): Result<Path> {
  val name = when {
    SystemInfo.isWindows -> "pipenv.exe"
    else -> "pipenv"
  }
  val executablePath = withContext(Dispatchers.IO) { PathEnvironmentVariableUtil.findInPath(name) }?.toPath()
  if (executablePath == null) {
    return Result.failure(FileNotFoundException("Cannot find $name in PATH"))
  }

  return Result.success(executablePath)
}

@Internal
fun detectPipEnvExecutableOrNull(): Path? {
  return runBlockingCancellable { detectPipEnvExecutable() }.getOrNull()
}

/**
 * Returns the configured pipenv executable or detects it automatically.
 */
@Internal
suspend fun getPipEnvExecutable(): Result<Path> =
  PropertiesComponent.getInstance().pipEnvPath?.let { Result.success(Path.of(it)) } ?: detectPipEnvExecutable()

/**
 * Sets up the pipenv environment under the modal progress window.
 *
 * The pipenv is associated with the first valid object from this list:
 *
 * 1. New project specified by [newProjectPath]
 * 2. Existing module specified by [module]
 * 3. Existing project specified by [project]
 *
 * @return the SDK for pipenv, not stored in the SDK table yet.
 */
@Internal
suspend fun setupPipEnvSdkUnderProgress(
  project: Project?,
  module: Module?,
  existingSdks: List<Sdk>,
  newProjectPath: String?,
  python: String?,
  installPackages: Boolean,
): Result<Sdk> {
  val projectPath = newProjectPath ?: module?.basePath ?: project?.basePath
                    ?: return Result.failure(FileNotFoundException("Can't find path to project or module"))
  val actualProject = project ?: module?.project
  val pythonExecutablePath = if (actualProject != null) {
    withBackgroundProgress(actualProject, PyBundle.message("python.sdk.setting.up.pipenv.title"), true) {
      setUpPipEnv(projectPath, python, installPackages)
    }
  }
  else {
    setUpPipEnv(projectPath, python, installPackages)
  }.getOrElse { return Result.failure(it) }

  return createSdk(pythonExecutablePath, existingSdks, projectPath, suggestedSdkName(projectPath),PyPipEnvSdkAdditionalData())
}

/**
 * Sets up the pipenv environment for the specified project path.
 *
 * @return the path to the pipenv environment.
 */
@Internal
suspend fun setupPipEnv(projectPath: Path, python: String?, installPackages: Boolean): Result<@SystemDependent String> {
  when {
    installPackages -> {
      val pythonArgs = if (python != null) listOf("--python", python) else emptyList()
      val command = pythonArgs + listOf("install", "--dev")
      runPipEnv(projectPath, *command.toTypedArray()).onFailure { return Result.failure(it) }
    }
    python != null ->
      runPipEnv(projectPath, "--python", python).onFailure { return Result.failure(it) }
    else ->
      runPipEnv(projectPath, "run", "python", "-V").onFailure { return Result.failure(it) }
  }
  return runPipEnv(projectPath, "--venv")
}

private suspend fun setUpPipEnv(projectPathString: String, python: String?, installPackages: Boolean): Result<Path> {
  val pipEnv = setupPipEnv(Path.of(projectPathString), python, installPackages).getOrElse { return Result.failure(it) }
  val pipEnvExecutablePathString = withContext(Dispatchers.IO) {
    VirtualEnvReader.Instance.findPythonInPythonRoot(Path.of(pipEnv))?.toString()
  } ?: return Result.failure(FileNotFoundException("Can't find pipenv in PATH"))
  return Result.success(Path.of(pipEnvExecutablePathString))
}