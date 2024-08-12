// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.execution.ExecutionException
import com.intellij.execution.RunCanceledByUserException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessNotCreatedException
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
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateExecutableFile
import com.jetbrains.python.sdk.*
import kotlinx.coroutines.launch
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
internal fun runPoetry(sdk: Sdk, vararg args: String): String {
  val projectPath = sdk.associatedModulePath?.let { Path.of(it) }
                    ?: throw PyExecutionException(PyBundle.message("python.sdk.poetry.execution.exception.no.project.message"),
                                                  "Poetry", emptyList(), ProcessOutput())
  runPoetry(projectPath, "env", "use", sdk.homePath!!)
  return runPoetry(projectPath, *args)
}

/**
 * Runs the configured poetry for the specified project path.
 */
fun runPoetry(projectPath: Path?, vararg args: String): String {
  val executable = getPoetryExecutable()
                   ?: throw PyExecutionException(PyBundle.message("python.sdk.poetry.execution.exception.no.poetry.message"), "poetry",
                                                 emptyList(), ProcessOutput())

  @Suppress("DialogTitleCapitalization")
  return runCommand(executable, projectPath, PyBundle.message("python.sdk.poetry.execution.exception.error.running.poetry.message"), *args)
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
  return runPoetry(projectPath, "env", "info", "-p")
}

private fun runCommand(projectPath: Path, command: String, vararg args: String): String {
  val commandLine = GeneralCommandLine(listOf(command) + args).withWorkingDirectory(projectPath)
  val handler = CapturingProcessHandler(commandLine)

  val result = with(handler) {
    runProcess()
  }
  return with(result) {
    @Suppress("DialogTitleCapitalization")
    when {
      isCancelled ->
        throw RunCanceledByUserException()
      exitCode != 0 ->
        throw PyExecutionException(PyBundle.message("python.sdk.poetry.execution.exception.error.running.poetry.message"), command,
                                   args.asList(),
                                   stdout, stderr, exitCode, emptyList())
      else -> stdout
    }
  }
}

internal fun runPoetryInBackground(module: Module, args: List<String>, @NlsSafe description: String) {
  module.project.service<PythonSdkRunCommandService>().cs.launch {
    withBackgroundProgress(module.project, "$description...", true) {
      val sdk = module.pythonSdk ?: return@withBackgroundProgress
      try {
        runPoetry(sdk, *args.toTypedArray())
      }
      catch (e: ExecutionException) {
        showSdkExecutionException(sdk, e, PyBundle.message("python.sdk.poetry.execution.exception.error.running.poetry.message"))
      }
      finally {
        PythonSdkUtil.getSitePackagesDirectory(sdk)?.refresh(true, true)
        sdk.associatedModuleDir?.refresh(true, false)
        PyPackageManager.getInstance(sdk).refreshAndGetPackages(true)
      }
    }
  }
}

internal fun detectPoetryEnvs(module: Module?, existingSdkPaths: Set<String>, projectPath: @SystemIndependent @NonNls String?): List<PyDetectedSdk> {
  val path = module?.basePath?.let { Path.of(it) } ?: projectPath?.let { Path.of(it) } ?: return emptyList()
  return try {
    getPoetryEnvs(path).filter { existingSdkPaths.contains(getPythonExecutable(it)) }.map { PyDetectedSdk(getPythonExecutable(it)) }
  }
  catch (e: Throwable) {
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
      try {
        val result = runPoetry(projectPath, *args)
        callback(result)
      }
      catch (e: PyExecutionException) {
        defaultResult
      }
      catch (e: ProcessNotCreatedException) {
        defaultResult
      }
    }.get(30, TimeUnit.SECONDS)
  }
  catch (e: TimeoutException) {
    defaultResult
  }
}

fun getPythonExecutable(homePath: String): String =  VirtualEnvReader.Instance.findPythonInPythonRoot(Path.of(homePath))?.toString() ?: FileUtil.join(homePath, "bin", "python")