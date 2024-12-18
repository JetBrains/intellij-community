// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.ProcessOutput
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.SystemProperties
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateExecutableFile
import com.jetbrains.python.sdk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.SystemDependent
import org.jetbrains.annotations.SystemIndependent
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString

/**
 *  This source code is edited by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */
private const val POETRY_PATH_SETTING: String = "PyCharm.Poetry.Path"
private const val REPLACE_PYTHON_VERSION = """import re,sys;f=open("pyproject.toml", "r+");orig=f.read();f.seek(0);f.write(re.sub(r"(python = \"\^)[^\"]+(\")", "\g<1>"+'.'.join(str(v) for v in sys.version_info[:2])+"\g<2>", orig))"""
private val poetryNotFoundException: PyExecutionException = PyExecutionException(PyBundle.message("python.sdk.poetry.execution.exception.no.poetry.message"), "poetry", emptyList(), ProcessOutput())

@Internal
suspend fun runPoetry(projectPath: Path?, vararg args: String): Result<String> {
  val executable = getPoetryExecutable().getOrElse { return Result.failure(it) }
  return runExecutable(executable, projectPath, *args)
}


/**
 * Tells if the SDK was added as poetry.
 * The user-set persisted a path to the poetry executable.
 */
var PropertiesComponent.poetryPath: @SystemDependent String?
  get() = getValue(POETRY_PATH_SETTING)
  set(value) {
    setValue(POETRY_PATH_SETTING, value)
  }

/**
 * Detects the poetry executable in `$PATH`.
 */
internal suspend fun detectPoetryExecutable(): Result<Path> {
  val name = when {
    SystemInfo.isWindows -> "poetry.bat"
    else -> "poetry"
  }

  val executablePath = withContext(Dispatchers.IO) {
    PathEnvironmentVariableUtil.findInPath(name)?.toPath() ?: SystemProperties.getUserHome().let { homePath ->
      Path.of(homePath, ".poetry", "bin", name).takeIf { it.exists() }
    }
  }
  return executablePath?.let { Result.success(it) } ?: Result.failure(poetryNotFoundException)
}

/**
 * Returns the configured poetry executable or detects it automatically.
 */
@Internal
suspend fun getPoetryExecutable(): Result<Path> = withContext(Dispatchers.IO) {
  PropertiesComponent.getInstance().poetryPath?.let { Path.of(it) }?.takeIf { it.exists() }
}?.let { Result.success(it) } ?: detectPoetryExecutable()

@Internal
suspend fun validatePoetryExecutable(poetryExecutable: Path?): ValidationInfo? = withContext(Dispatchers.IO) {
  validateExecutableFile(ValidationRequest(path = poetryExecutable?.pathString, fieldIsEmpty = PyBundle.message("python.sdk.poetry.executable.not.found"), platformAndRoot = PlatformAndRoot.local // TODO: pass real converter from targets when we support poetry @ targets
  ))
}

/**
 * Runs poetry command for the specified Poetry SDK.
 * Runs:
 * 1. `poetry env use [sdk]`
 * 2. `poetry [args]`
 */
internal suspend fun runPoetryWithSdk(sdk: Sdk, vararg args: String): Result<String> {
  val projectPath = sdk.associatedModulePath?.let { Path.of(it) } ?: return Result.failure(poetryNotFoundException) // Choose a correct sdk
  runPoetry(projectPath, "env", "use", sdk.homePath!!)
  return runPoetry(projectPath, *args)
}


/**
 * Sets up the poetry environment for the specified project path.
 *
 * @return the path to the poetry environment.
 */
@Internal
suspend fun setupPoetry(projectPath: Path, python: String?, installPackages: Boolean, init: Boolean): Result<@SystemDependent String> {
  if (init) {
    runPoetry(projectPath, *listOf("init", "-n").toTypedArray())
    if (python != null) { // Replace a python version in toml
      runCommand(projectPath, python, "-c", REPLACE_PYTHON_VERSION)
    }
  }
  when {
    installPackages -> {
      python?.let { runPoetry(projectPath, "env", "use", it) }
      runPoetry(projectPath, "install")
    }
    python != null -> runPoetry(projectPath, "env", "use", python)
    else -> runPoetry(projectPath, "run", "python", "-V")
  }

  return runPoetry(projectPath, "env", "info", "-p")
}

internal fun runPoetryInBackground(module: Module, args: List<String>, @NlsSafe description: String) {
  service<PythonSdkCoroutineService>().cs.launch {
    withBackgroundProgress(module.project, "$description...", true) {
      val sdk = module.pythonSdk ?: return@withBackgroundProgress
      try {
        val result = runPoetryWithSdk(sdk, *args.toTypedArray()).exceptionOrNull()
        if (result is ExecutionException) {
          withContext(Dispatchers.EDT) {
            showSdkExecutionException(sdk, result, PyBundle.message("sdk.create.custom.venv.run.error.message", "poetry"))
          }
        }
      }
      finally {
        PythonSdkUtil.getSitePackagesDirectory(sdk)?.refresh(true, true)
        sdk.associatedModuleDir?.refresh(true, false)
        PythonPackageManager.forSdk(module.project, sdk).reloadPackages()
        PyPackageManager.getInstance(sdk).refreshAndGetPackages(true)
      }
    }
  }
}

internal suspend fun detectPoetryEnvs(module: Module?, existingSdkPaths: Set<String>, projectPath: @SystemIndependent @NonNls String?): List<PyDetectedSdk> {
  val path = module?.basePath?.let { Path.of(it) } ?: projectPath?.let { Path.of(it) } ?: return emptyList()
  return getPoetryEnvs(path).filter { existingSdkPaths.contains(getPythonExecutable(it)) }.map { PyDetectedSdk(getPythonExecutable(it)) }
}

internal suspend fun getPoetryVersion(): String? = runPoetry(null, "--version").getOrNull()?.split(' ')?.lastOrNull()

@Internal
suspend fun getPythonExecutable(homePath: String): String = withContext(Dispatchers.IO) {
  VirtualEnvReader.Instance.findPythonInPythonRoot(Path.of(homePath))?.toString() ?: FileUtil.join(homePath, "bin", "python")
}

/**
 * Installs a Python package using Poetry.
 * Runs `poetry add [pkg] [extraArgs]`
 *
 * @param [pkg] The name of the package to be installed.
 * @param [extraArgs] Additional arguments to pass to the Poetry add command.
 */
@Internal
suspend fun poetryInstallPackage(sdk: Sdk, pkg: String, extraArgs: List<String>): Result<String> {
  val args = listOf("add", pkg) + extraArgs
  return runPoetryWithSdk(sdk, *args.toTypedArray())
}

/**
 * Uninstalls a Python package using Poetry.
 * Runs `poetry remove [pkg]`
 *
 * @param [pkg] The name of the package to be uninstalled.
 */
@Internal
suspend fun poetryUninstallPackage(sdk: Sdk, pkg: String): Result<String> = runPoetryWithSdk(sdk, "remove", pkg)

@Internal
suspend fun poetryReloadPackages(sdk: Sdk): Result<String> {
  runPoetryWithSdk(sdk, "update").onFailure { return Result.failure(it) }
  runPoetryWithSdk(sdk, "install", "--no-root").onFailure { return Result.failure(it) }
  return runPoetryWithSdk(sdk, "show")
}

private suspend fun getPoetryEnvs(projectPath: Path): List<String> {
  val executionResult = runPoetry(projectPath, "env", "list", "--full-path")
  return executionResult.getOrNull()?.lineSequence()?.map { it.split(" ")[0] }?.filterNot { it.isEmpty() }?.toList() ?: emptyList()
}
