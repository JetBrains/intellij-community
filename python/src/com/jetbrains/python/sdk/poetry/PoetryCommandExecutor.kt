// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
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
import com.intellij.python.community.impl.poetry.poetryPath
import com.intellij.util.SystemProperties
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateExecutableFile
import com.jetbrains.python.sdk.*
import com.jetbrains.python.venvReader.VirtualEnvReader
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
private const val REPLACE_PYTHON_VERSION = """import re,sys;f=open("pyproject.toml", "r+");orig=f.read();f.seek(0);f.write(re.sub(r"(python = \"\^)[^\"]+(\")", "\g<1>"+'.'.join(str(v) for v in sys.version_info[:2])+"\g<2>", orig))"""
private val poetryNotFoundException: Throwable = Throwable(PyBundle.message("python.sdk.poetry.execution.exception.no.poetry.message"))

@Internal
suspend fun runPoetry(projectPath: Path?, vararg args: String): Result<String> {
  val executable = getPoetryExecutable().getOrElse { return Result.failure(it) }
  return runExecutable(executable, projectPath, *args)
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

internal suspend fun detectPoetryEnvs(module: Module?, existingSdkPaths: Set<String>?, projectPath: @SystemIndependent @NonNls String?): List<PyDetectedSdk> {
  val path = module?.basePath?.let { Path.of(it) } ?: projectPath?.let { Path.of(it) } ?: return emptyList()
  return getPoetryEnvs(path).filter { existingSdkPaths?.contains(getPythonExecutable(it)) != false }.map { PyDetectedSdk(getPythonExecutable(it)) }
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
fun parsePoetryShow(input: String): List<PythonPackage> {
  val result = mutableListOf<PythonPackage>()
  input.split("\n").forEach { line ->
    if (line.isNotBlank()) {
      val packageInfo = line.trim().split(" ").map { it.trim() }.filter { it.isNotBlank() && it != "(!)" }
      result.add(PythonPackage(packageInfo[0], packageInfo[1], false))
    }
  }

  return result
}

@Internal
suspend fun poetryShowOutdated(sdk: Sdk): Result<Map<String, PythonOutdatedPackage>> {
  val output = runPoetryWithSdk(sdk, "show", "--outdated").getOrElse {
    return Result.failure(it)
  }

  return parsePoetryShowOutdated(output).let { Result.success(it) }
}

@Internal
suspend fun poetryListPackages(sdk: Sdk): Result<Pair<List<PyPackage>, List<PyRequirement>>> {
  // Just in case there were any changes to pyproject.toml
  if (runPoetryWithSdk(sdk, "lock", "--check").isFailure) {
    if (runPoetryWithSdk(sdk, "lock", "--no-update").isFailure) {
      runPoetryWithSdk(sdk, "lock")
    }
  }

  val output = runPoetryWithSdk(sdk, "install", "--dry-run", "--no-root").getOrElse {
    return Result.failure(it)
  }

  return parsePoetryInstallDryRun(output).let {
    Result.success(it)
  }
}

@Internal
fun parsePoetryInstallDryRun(input: String): Pair<List<PyPackage>, List<PyRequirement>> {
  val installedLines = listOf("Already installed", "Skipping", "Updating")

  fun getNameAndVersion(line: String): Triple<String, String, String> {
    return line.split(" ").let {
      val installedVersion = it[5].replace(Regex("[():]"), "")
      val requiredVersion = when {
        it.size > 7 && it[6] == "->" -> it[7].replace(Regex("[():]"), "")
        else -> installedVersion
      }
      Triple(it[4], installedVersion, requiredVersion)
    }
  }

  fun getVersion(version: String): String {
    return if (Regex("^[0-9]").containsMatchIn(version)) "==$version" else version
  }

  val pyPackages = mutableListOf<PyPackage>()
  val pyRequirements = mutableListOf<PyRequirement>()
  input
    .lineSequence()
    .filter { listOf(")", "Already installed").any { lastWords -> it.endsWith(lastWords) } }
    .forEach { line ->
      getNameAndVersion(line).also {
        if (installedLines.any { installedLine -> line.contains(installedLine) }) {
          pyPackages.add(PyPackage(it.first, it.second, null, emptyList()))
          PyRequirementParser.fromLine(it.first + getVersion(it.third))?.let { pyRequirement -> pyRequirements.add(pyRequirement) }
        }
        else if (line.contains("Installing")) {
          PyRequirementParser.fromLine(it.first + getVersion(it.third))?.let { pyRequirement -> pyRequirements.add(pyRequirement) }
        }
      }
    }

  return Pair(pyPackages.distinct().toList(), pyRequirements.distinct().toList())
}

/**
 * Configures the Poetry environment for the specified module path with the given arguments.
 * Runs command: GeneralCommandLine("poetry config [args]").withWorkingDirectory([modulePath])
 *
 * @param [modulePath] The path to the module where the Poetry environment is to be configured.
 * Can be null, in which case the global Poetry environment will be configured.
 * @param [args] A vararg array of String arguments to pass to the Poetry configuration command.
 */
@Internal
suspend fun configurePoetryEnvironment(modulePath: Path?, vararg args: String) {
  runPoetry(modulePath, "config", *args)
}

private suspend fun getPoetryEnvs(projectPath: Path): List<String> {
  val executionResult = runPoetry(projectPath, "env", "list", "--full-path")
  return executionResult.getOrNull()?.lineSequence()?.map { it.split(" ")[0] }?.filterNot { it.isEmpty() }?.toList() ?: emptyList()
}
