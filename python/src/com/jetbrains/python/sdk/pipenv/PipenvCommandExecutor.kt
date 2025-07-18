// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.where
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.runExecutableWithProgress
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.SystemDependent
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

@Internal
suspend fun runPipEnv(dirPath: Path?, vararg args: String): PyResult<String> {
  val executable = getPipEnvExecutable().getOr { return it }
  return runExecutableWithProgress(executable, dirPath, 10.minutes, args = args)
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
suspend fun detectPipEnvExecutable(): PyResult<Path> {
  val name = when {
    SystemInfo.isWindows -> "pipenv.exe"
    else -> "pipenv"
  }
  val executablePath = localEel.exec.where(name)?.asNioPath()
  if (executablePath == null) {
    return PyResult.localizedError(PyBundle.message("cannot.find.executable", name, localEel.descriptor.machine.name))
  }

  return PyResult.success(executablePath)
}

@Internal
fun detectPipEnvExecutableOrNull(): Path? {
  return runBlockingCancellable { detectPipEnvExecutable() }.getOrNull()
}

/**
 * Returns the configured pipenv executable or detects it automatically.
 */
@Internal
suspend fun getPipEnvExecutable(): PyResult<Path> =
  PropertiesComponent.getInstance().pipEnvPath?.let { PyResult.success(Path.of(it)) } ?: detectPipEnvExecutable()

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
suspend fun setupPipEnvSdkWithProgressReport(
  project: Project?,
  module: Module?,
  existingSdks: List<Sdk>,
  newProjectPath: String?,
  python: String?,
  installPackages: Boolean,
): PyResult<Sdk> {
  val projectPath = newProjectPath ?: module?.basePath ?: project?.basePath
                    ?: return PyResult.localizedError(PyBundle.message("python.sdk.provided.path.is.invalid", null))
  val actualProject = project ?: module?.project
  val pythonExecutablePath = if (actualProject != null) {
    setUpPipEnv(projectPath, python, installPackages)
  }
  else {
    setUpPipEnv(projectPath, python, installPackages)
  }.getOr { return it }

  return createSdk(pythonExecutablePath, existingSdks, projectPath, suggestedSdkName(projectPath), PyPipEnvSdkAdditionalData())
}

/**
 * Sets up the pipenv environment for the specified project path.
 *
 * @return the path to the pipenv environment.
 */
@Internal
suspend fun setupPipEnv(projectPath: Path, python: String?, installPackages: Boolean): PyResult<@SystemDependent String> {
  when {
    installPackages -> {
      val pythonArgs = if (python != null) listOf("--python", python) else emptyList()
      val command = pythonArgs + listOf("install", "--dev")
      runPipEnv(projectPath, *command.toTypedArray()).getOr { return it }
    }
    python != null ->
      runPipEnv(projectPath, "--python", python).getOr { return it }
    else ->
      runPipEnv(projectPath, "run", "python", "-V").getOr { return it }
  }
  return runPipEnv(projectPath, "--venv")
}

private suspend fun setUpPipEnv(projectPathString: String, python: String?, installPackages: Boolean): PyResult<Path> {
  val pipEnv = setupPipEnv(Path.of(projectPathString), python, installPackages).getOr { return it }
  val pipEnvExecutablePathString = withContext(Dispatchers.IO) {
    VirtualEnvReader.Instance.findPythonInPythonRoot(Path.of(pipEnv))?.toString()
  } ?: return PyResult.localizedError(PyBundle.message("python.sdk.provided.path.is.invalid", pipEnv))
  return PyResult.success(Path.of(pipEnvExecutablePathString))
}