// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.execution.ExecutionException
import com.intellij.execution.RunCanceledByUserException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
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
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateExecutableFile
import com.jetbrains.python.sdk.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.SystemDependent
import org.jetbrains.annotations.SystemIndependent
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.exists
import kotlin.io.path.pathString

/**
 *  This source code is edited by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */
private const val POETRY_PATH_SETTING: String = "PyCharm.Poetry.Path"
private const val REPLACE_PYTHON_VERSION = """import re,sys;f=open("pyproject.toml", "r+");orig=f.read();f.seek(0);f.write(re.sub(r"(python = \"\^)[^\"]+(\")", "\g<1>"+'.'.join(str(v) for v in sys.version_info[:2])+"\g<2>", orig))"""

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
internal fun detectPoetryExecutable(): Path? {
  val name = when {
    SystemInfo.isWindows -> "poetry.bat"
    else -> "poetry"
  }
  return PathEnvironmentVariableUtil.findInPath(name)?.toPath() ?: SystemProperties.getUserHome().let { homePath ->
    Path.of(homePath, ".poetry", "bin", name).takeIf { it.exists() }
  }
}

/**
 * Returns the configured poetry executable or detects it automatically.
 */
fun getPoetryExecutable(): Path? =
  PropertiesComponent.getInstance().poetryPath?.let { Path.of(it) }?.takeIf { it.exists() } ?: detectPoetryExecutable()

fun validatePoetryExecutable(poetryExecutable: Path?): ValidationInfo? =
  validateExecutableFile(ValidationRequest(
    path = poetryExecutable?.pathString,
    fieldIsEmpty = PyBundle.message("python.sdk.poetry.executable.not.found"),
    platformAndRoot = PlatformAndRoot.local // TODO: pass real converter from targets when we support poetry @ targets
  ))

/**
 * Runs the configured poetry for the specified Poetry SDK with the associated project path.
 */
internal fun runPoetry(sdk: Sdk, vararg args: String): Result<String> {
  val projectPath = sdk.associatedModulePath?.let { Path.of(it) }
                    ?: throw PyExecutionException(PyBundle.message("python.sdk.poetry.execution.exception.no.project.message"),
                                                  "Poetry", emptyList(), ProcessOutput())
  runPoetry(projectPath, "env", "use", sdk.homePath!!)
  return runPoetry(projectPath, *args)
}

/**
 * Runs the configured poetry for the specified project path.
 */
fun runPoetry(projectPath: Path?, vararg args: String): Result<String> {
  val executable = getPoetryExecutable()
                   ?: return Result.failure(PyExecutionException(PyBundle.message("python.sdk.poetry.execution.exception.no.poetry.message"), "poetry",
                                                 emptyList(), ProcessOutput()))

  return runCommand(executable, projectPath, PyBundle.message("sdk.create.custom.venv.run.error.message", "poetry"), *args)
}

/**
 * Sets up the poetry environment for the specified project path.
 *
 * @return the path to the poetry environment.
 */
fun setupPoetry(projectPath: Path, python: String?, installPackages: Boolean, init: Boolean): @SystemDependent String {
  if (init) {
    runPoetry(projectPath, *listOf("init", "-n").toTypedArray())
    if (python != null) {
      // Replace a python version in toml
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

  return runPoetry(projectPath, "env", "info", "-p").getOrThrow()
}

private fun runCommand(projectPath: Path, command: String, vararg args: String): Result<String> {
  val commandLine = GeneralCommandLine(listOf(command) + args).withWorkingDirectory(projectPath)
  val handler = CapturingProcessHandler(commandLine)

  val result = with(handler) {
    runProcess()
  }
  return with(result) {
    when {
      isCancelled ->
        Result.failure(RunCanceledByUserException())
      exitCode != 0 ->
        Result.failure(PyExecutionException(PyBundle.message("sdk.create.custom.venv.run.error.message", "poetry"), command,
                                            args.asList(),
                                            stdout, stderr, exitCode, emptyList()))
      else -> Result.success(stdout)
    }
  }
}

internal fun runPoetryInBackground(module: Module, args: List<String>, @NlsSafe description: String) {
  service<PythonSdkCoroutineService>().cs.launch {
    withBackgroundProgress(module.project, "$description...", true) {
      val sdk = module.pythonSdk ?: return@withBackgroundProgress
      try {
        val result = runPoetry(sdk, *args.toTypedArray()).exceptionOrNull()
        if (result is ExecutionException) {
          showSdkExecutionException(sdk, result, PyBundle.message("sdk.create.custom.venv.run.error.message", "poetry"))
        }
      }
      finally {
        PythonSdkUtil.getSitePackagesDirectory(sdk)?.refresh(true, true)
        sdk.associatedModuleDir?.refresh(true, false)
        PythonPackageManager.forSdk(module.project, sdk).reloadPackages()
      }
    }
  }
}

internal fun detectPoetryEnvs(module: Module?, existingSdkPaths: Set<String>, projectPath: @SystemIndependent @NonNls String?): List<PyDetectedSdk> {
  val path = module?.basePath?.let { Path.of(it) } ?: projectPath?.let { Path.of(it) } ?: return emptyList()
  return try {
    getPoetryEnvs(path).filter { existingSdkPaths.contains(getPythonExecutable(it)) }.map { PyDetectedSdk(getPythonExecutable(it)) }
  }
  catch (_: Throwable) {
    emptyList()
  }
}

private fun getPoetryEnvs(projectPath: Path): List<String> =
  syncRunPoetry(projectPath, "env", "list", "--full-path", defaultResult = emptyList()) { result ->
    result.lineSequence().map { it.split(" ")[0] }.filterNot { it.isEmpty() }.toList()
  }

internal val poetryVersion: String?
  get() = syncRunPoetry(null, "--version", defaultResult = "") {
    it.split(' ').lastOrNull()
  }

inline fun <reified T> syncRunPoetry(
  projectPath: Path?,
  vararg args: String,
  defaultResult: T,
  crossinline callback: (String) -> T,
): T {
  return try {
    ApplicationManager.getApplication().executeOnPooledThread<T> {
      val result = runPoetry(projectPath, *args).getOrNull()
      if (result == null) defaultResult else callback(result)
    }.get(30, TimeUnit.SECONDS)
  }
  catch (_: TimeoutException) {
    defaultResult
  }
}

fun getPythonExecutable(homePath: String): String = VirtualEnvReader.Instance.findPythonInPythonRoot(Path.of(homePath))?.toString()
                                                    ?: FileUtil.join(homePath, "bin", "python")


/**
 * Installs a Python package using Poetry.
 * Runs `poetry add [pkg] [extraArgs]`
 *
 * @param [pkg] The name of the package to be installed.
 * @param [extraArgs] Additional arguments to pass to the Poetry add command.
 */
@Internal
fun poetryInstallPackage(sdk: Sdk, pkg: String, extraArgs: List<String>): Result<String> {
  val args = listOf("add", pkg) + extraArgs
  return runPoetry(sdk, *args.toTypedArray())
}

/**
 * Uninstalls a Python package using Poetry.
 * Runs `poetry remove [pkg]`
 *
 * @param [pkg] The name of the package to be uninstalled.
 */
@Internal
fun poetryUninstallPackage(sdk: Sdk, pkg: String): Result<String> = runPoetry(sdk, "remove", pkg)

@Internal
fun poetryReloadPackages(sdk: Sdk): Result<String> {
  runPoetry(sdk, "update").onFailure { return Result.failure(it) }
  runPoetry(sdk, "install", "--no-root").onFailure { return Result.failure(it) }
  return runPoetry(sdk, "show")
}
