// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.impl.pipenv.pipenvPath
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.SystemDependent
import java.nio.file.Path
import kotlin.io.path.pathString

private val PIP_TOOL: ToolCommandExecutor = ToolCommandExecutor("pip") {
  pipenvPath
}

@Internal
suspend fun runPipEnv(dirPath: Path?, vararg args: String): PyResult<String> = PIP_TOOL.runTool(dirPath, *args)

@Internal
suspend fun <T> runPipEnv(dirPath: Path?, vararg args: String, transformer: ProcessOutputTransformer<T>): PyResult<T> = PIP_TOOL.runTool(dirPath = dirPath, args = args, transformer = transformer)


@Internal
@JvmOverloads
internal fun detectPipEnvExecutableOrNull(eel: EelApi = localEel): Path?  = PIP_TOOL.detectToolExecutableOrNull(eel)

/**
 * Returns the configured pipenv executable or detects it automatically.
 */
@Internal
suspend fun getPipEnvExecutable(eel: EelApi = localEel): Path? =  PIP_TOOL.getToolExecutable(eel)

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
  moduleBasePath: Path,
  existingSdks: List<Sdk>,
  basePythonBinaryPath: PythonBinary?,
  installPackages: Boolean,
): PyResult<Sdk> {
  val pythonExecutablePath = setUpPipEnv(moduleBasePath, basePythonBinaryPath, installPackages).getOr { return it }

  return createSdk(
    PathHolder.Eel(pythonExecutablePath),
    existingSdks, moduleBasePath.pathString,
    suggestedSdkName(moduleBasePath.pathString),
    PyPipEnvSdkAdditionalData()
  )
}

/**
 * Sets up the pipenv environment for the specified project path.
 *
 * @return the path to the pipenv environment.
 */
@Internal
suspend fun setupPipEnv(projectPath: Path, basePythonBinaryPath: PythonBinary?, installPackages: Boolean): PyResult<@SystemDependent String> {
  when {
    installPackages -> {
      val pythonArgs = if (basePythonBinaryPath != null) listOf("--python", basePythonBinaryPath.pathString) else emptyList()
      val command = pythonArgs + listOf("install", "--dev")
      runPipEnv(projectPath, *command.toTypedArray()).getOr { return it }
    }
    basePythonBinaryPath != null ->
      runPipEnv(projectPath, "--python", basePythonBinaryPath.pathString).getOr { return it }
    else ->
      runPipEnv(projectPath, "run", "python", "-V").getOr { return it }
  }
  return runPipEnv(projectPath, "--venv")
}

private suspend fun setUpPipEnv(moduleBasePath: Path, basePythonBinaryPath: PythonBinary?, installPackages: Boolean): PyResult<Path> {
  val pipEnv = setupPipEnv(moduleBasePath, basePythonBinaryPath, installPackages).getOr { return it }
  val pipEnvExecutablePathString = withContext(Dispatchers.IO) {
    VirtualEnvReader.Instance.findPythonInPythonRoot(Path.of(pipEnv))?.toString()
  } ?: return PyResult.localizedError(PyBundle.message("python.sdk.provided.path.is.invalid", pipEnv))
  return PyResult.success(Path.of(pipEnvExecutablePathString))
}
