// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv

import com.intellij.openapi.components.service
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.execService.DownloadConfig
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.impl.pipenv.pipenvPath
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.ToolCommandExecutor
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.TargetFileSystemCache
import com.jetbrains.python.sdk.add.v2.toEelFileSystem
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.pySdkAdditionalData
import com.jetbrains.python.sdk.runTool
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.PythonLanguageRuntimeConfiguration
import com.jetbrains.python.target.ui.TargetPanelExtension
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.SystemDependent
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.exists

internal val PIPENV_TOOL: ToolCommandExecutor = ToolCommandExecutor("pipenv") {
  pipenvPath
}

private val PIPENV_PROJECT_DOWNLOAD_CONFIG = DownloadConfig(relativePaths = listOf(PIP_FILE, PIP_FILE_LOCK))
private val PIPENV_PROJECT_MUTATING_COMMANDS = setOf("--python", "install", "lock", "sync", "uninstall", "update")

internal suspend fun <P : PathHolder> runPipEnv(
  fileSystem: FileSystem<P>,
  dirPath: Path?,
  vararg args: String,
  pipenvExecutable: P? = null,
  baseEnv: Map<String, String> = emptyMap(),
  downloadConfig: DownloadConfig? = null,
): PyResult<String> =
  PIPENV_TOOL.runTool(
    fileSystem = fileSystem,
    pathFromSdk = pipenvExecutable?.toString(),
    dirPath = dirPath,
    args = args,
    env = baseEnv,
    downloadConfig = downloadConfig,
  )

@Internal
@PyInternalExecApi
suspend fun runPipEnv(dirPath: Path?, vararg args: String): PyResult<String> =
  runPipEnv(
    fileSystem = dirPath.toEelFileSystem(),
    dirPath = dirPath,
    args = args,
  )

internal suspend fun <T> runPipEnv(dirPath: Path?, vararg args: String, transformer: ProcessOutputTransformer<T>): PyResult<T> =
  PIPENV_TOOL.runTool(
    fileSystem = dirPath.toEelFileSystem(),
    pathFromSdk = null,
    dirPath = dirPath,
    args = args,
    transformer = transformer,
  )

/**
 * Returns the configured pipenv executable or detects it automatically on the given [fileSystem].
 */
internal suspend fun <P : PathHolder> getPipEnvExecutable(fileSystem: FileSystem<P>): P? =
  PIPENV_TOOL.getToolExecutable(fileSystem, pathFromSdk = null)

/**
 * Returns the configured pipenv executable or detects it automatically.
 */
internal suspend fun getPipEnvExecutable(eel: EelApi = localEel): Path? =
  getPipEnvExecutable(EelFileSystem(eel))?.path

internal suspend fun runPipEnvWithSdk(sdk: Sdk, vararg args: String): PyResult<String> {
  val data = sdk.pySdkAdditionalData
  val workingDirectory = data.workingDirectory.takeIf { data.hasValidWorkingDirectory() }
                         ?: return PyResult.localizedError(PyBundle.message("python.sdk.project.working.directory.not.found"))
  val sdkHomePath = sdk.homePath
                    ?: return PyResult.localizedError(PySdkBundle.message("python.sdk.broken.configuration", sdk.name))

  return when (data) {
    is PyTargetAwareAdditionalData -> {
      val targetConfig = data.targetEnvironmentConfiguration
                         ?: return PyResult.localizedError(PySdkBundle.message("python.sdk.broken.configuration", sdk.name))
      runPipEnvWithSdk(
        fileSystem = service<TargetFileSystemCache>().getOrCreate(targetConfig, PythonLanguageRuntimeConfiguration()),
        workingDirectory = workingDirectory,
        sdkHomePath = sdkHomePath,
        args = args,
      )
    }
    else -> runPipEnvWithSdk(
      fileSystem = workingDirectory.toEelFileSystem(),
      workingDirectory = workingDirectory,
      sdkHomePath = sdkHomePath,
      args = args,
    )
  }
}

private suspend fun <P : PathHolder> runPipEnvWithSdk(
  fileSystem: FileSystem<P>,
  workingDirectory: Path,
  sdkHomePath: String,
  vararg args: String,
): PyResult<String> {
  val pythonPath = fileSystem.parsePath(sdkHomePath).getOr { return it }
  val pythonHomePath = fileSystem.resolvePythonHome(pythonPath)
  return runPipEnv(
    fileSystem = fileSystem,
    dirPath = workingDirectory,
    args = args,
    baseEnv = mapOf("VIRTUAL_ENV" to pythonHomePath.toString()),
    downloadConfig = PIPENV_PROJECT_DOWNLOAD_CONFIG.takeIf { args.firstOrNull() in PIPENV_PROJECT_MUTATING_COMMANDS },
  )
}

/**
 * Sets up the pipenv environment for [moduleBasePath] and creates its SDK.
 *
 * @return the SDK for pipenv, not stored in the SDK table yet.
 */
internal suspend fun <P : PathHolder> setupPipEnvSdkWithProgressReport(
  moduleBasePath: Path,
  basePythonBinaryPath: P?,
  fileSystem: FileSystem<P>,
  pipenvExecutable: P?,
  installPackages: Boolean,
  targetPanelExtension: TargetPanelExtension? = null,
): PyResult<Sdk> {
  val pythonHomePath = setupPipEnv(
    projectPath = moduleBasePath,
    fileSystem = fileSystem,
    pipenvExecutable = pipenvExecutable,
    basePythonBinaryPath = basePythonBinaryPath,
    installPackages = installPackages,
  ).getOr { return it }
  val pythonBinaryPath = fileSystem.resolvePythonBinary(pythonHomePath)
                         ?: return PyResult.localizedError(PyBundle.message("python.sdk.cannot.setup.sdk", pythonHomePath))

  return fileSystem.setupSdk(
    project = null,
    pythonBinaryPath = pythonBinaryPath,
    sdkAdditionalData = PyPipEnvSdkAdditionalData(moduleBasePath),
    targetPanelExtension = targetPanelExtension,
    suggestedSdkName = null,
  )
}

/**
 * Sets up the pipenv environment for the specified project path.
 *
 * @return the path to the pipenv environment.
 */
internal suspend fun setupPipEnv(
  projectPath: Path,
  basePythonBinaryPath: PythonBinary?,
  installPackages: Boolean,
): PyResult<@SystemDependent String> =
  setupPipEnv(
    projectPath = projectPath,
    fileSystem = projectPath.toEelFileSystem(),
    pipenvExecutable = null,
    basePythonBinaryPath = basePythonBinaryPath?.let(PathHolder::Eel),
    installPackages = installPackages,
  ).mapSuccess { it.toString() }

internal suspend fun <P : PathHolder> setupPipEnv(
  projectPath: Path,
  fileSystem: FileSystem<P>,
  pipenvExecutable: P?,
  basePythonBinaryPath: P?,
  installPackages: Boolean,
): PyResult<P> {
  val pipfile = projectPath.resolve(PIP_FILE)

  if (!pipfile.exists()) {
    // Currently, if a Pipenv file exists inside the user's home directory, then it will NOT create a new Pipenv in the current project
    // directory, but instead use the one in the home directory. This has an effect that new projects are created without the Pipenv file,
    // which results in a broken project setup. If an empty Pipenv file is created in the project directory beforehand, then pipenv will use
    // and populate that file instead.
    pipfile.createFile()
  }

  when {
    installPackages -> {
      runPipEnv(
        fileSystem = fileSystem,
        dirPath = projectPath,
        args = pipenvSetupCommandWithPythonPath(projectPath, basePythonBinaryPath?.toString()).toTypedArray(),
        pipenvExecutable = pipenvExecutable,
        downloadConfig = PIPENV_PROJECT_DOWNLOAD_CONFIG,
      ).getOr { return it }
    }
    basePythonBinaryPath != null ->
      runPipEnv(
        fileSystem = fileSystem,
        dirPath = projectPath,
        "--python", basePythonBinaryPath.toString(),
        pipenvExecutable = pipenvExecutable,
        downloadConfig = PIPENV_PROJECT_DOWNLOAD_CONFIG,
      ).getOr { return it }
    else ->
      runPipEnv(
        fileSystem = fileSystem,
        dirPath = projectPath,
        "run", "python", "-V",
        pipenvExecutable = pipenvExecutable,
      ).getOr { return it }
  }
  val pythonHomePath = runPipEnv(
    fileSystem = fileSystem,
    dirPath = projectPath,
    "--venv",
    pipenvExecutable = pipenvExecutable,
  ).getOr { return it }
  return fileSystem.parsePath(pythonHomePath.trim())
}

internal fun pipenvSetupCommand(projectPath: Path, basePythonBinaryPath: PythonBinary?): List<String> =
  pipenvSetupCommandWithPythonPath(projectPath, basePythonBinaryPath?.toString())

private fun pipenvSetupCommandWithPythonPath(projectPath: Path, basePythonBinaryPath: String?): List<String> {
  val pythonArgs = if (basePythonBinaryPath != null) listOf("--python", basePythonBinaryPath) else emptyList()
  val command = if (projectPath.resolve(PIP_FILE_LOCK).exists()) listOf("sync", "--dev") else listOf("install", "--dev")
  return pythonArgs + command
}
