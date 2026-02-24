// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.execService.python.validatePythonAndGetInfo
import com.intellij.python.community.impl.poetry.common.poetryPath
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.isSuccess
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.sdk.ToolCommandExecutor
import com.jetbrains.python.sdk.associatedModulePath
import com.jetbrains.python.sdk.runTool
import com.jetbrains.python.venvReader.VirtualEnvReader
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.toVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 *  This source code is edited by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */
private val poetryNotFoundException: @Nls String = PyBundle.message("python.sdk.poetry.execution.exception.no.poetry.message")
private val VERSION_2 = "2.0.0".toVersion()


private val POETRY_TOOL: ToolCommandExecutor = ToolCommandExecutor(
  "poetry",
  getAdditionalSearchPaths = {
    // TODO: Poetry from store isn't detected because local eel doesn't obey appx binaries. We need to fix it on eel side
    listOf(userInfo.home.asNioPath().resolve(Path.of(".poetry", ".bin")))
  },
  getToolPathFromSettings = {
    poetryPath
  })

private val POETRY_EXCLUDE_NON_DIGITS_REGEX = Regex("""\D+$""")

@Internal
suspend fun runPoetry(projectPath: Path?, vararg args: String): PyResult<String> = POETRY_TOOL.runTool(projectPath, *args)


/**
 * Returns the configured poetry executable or detects it automatically.
 */
@Internal
suspend fun getPoetryExecutable(eel: EelApi = localEel): Path? = POETRY_TOOL.getToolExecutable(eel)

/**
 * Runs poetry command for the specified Poetry SDK.
 * Runs:
 * 1. `poetry env use [sdk]`
 * 2. `poetry [args]`
 */
@Internal
suspend fun runPoetryWithSdk(sdk: Sdk, vararg args: String): PyResult<String> {
  val projectPath = sdk.associatedModulePath?.let { Path.of(it) }
                    ?: return PyResult.localizedError(poetryNotFoundException) // Choose a correct sdk
  runPoetry(projectPath, "env", "use", sdk.homePath!!)
  return runPoetry(projectPath, *args)
}


/**
 * Sets up the poetry environment for the specified project path.
 *
 * @return the path to the poetry environment.
 */
@Internal
suspend fun setupPoetry(
  projectPath: Path,
  basePythonBinaryPath: PythonBinary,
  installPackages: Boolean,
  init: Boolean,
): PyResult<PythonHomePath> {
  if (init) {
    // Build poetry init command with Python version constraint if available
    val initArgs = mutableListOf("init", "-n")

    // Validate Python and get version info
    val pythonInfo = basePythonBinaryPath.validatePythonAndGetInfo().getOr { return it }
    val major = pythonInfo.languageLevel.majorVersion
    val minor = pythonInfo.languageLevel.minorVersion
    // Add --python flag with caret constraint (e.g., "^3.10")
    initArgs.add("--python")
    initArgs.add("^$major.$minor")

    runPoetry(projectPath, *initArgs.toTypedArray()).getOr { return it }
  }

  runPoetry(projectPath, "env", "use", basePythonBinaryPath.pathString).getOr { return it }

  if (installPackages) {
    runPoetry(projectPath, "install", "--no-root").getOr { return it }
  }

  return runPoetry(projectPath, "env", "info", "-p").mapSuccess { Path.of(it) }
}

internal suspend fun detectPoetryEnvs(searchPath: Path): List<PythonBinary> = getPoetryEnvs(searchPath).mapNotNull { getPythonExecutable(it) }

internal suspend fun getPoetryVersion(): String? =
  runPoetry(null, "--version")
    .getOrNull()
    ?.split(' ')
    ?.lastOrNull()
    ?.replace(POETRY_EXCLUDE_NON_DIGITS_REGEX, "") // strip all non-numeric characters after the version

private suspend fun getPythonExecutable(homePathString: String): PythonBinary? = withContext(Dispatchers.IO) {
  VirtualEnvReader().findPythonInPythonRoot(Path.of(homePathString))
}

/**
 * Installs a Python package using Poetry.
 * Runs `poetry add [packages] [extraArgs]`
 *
 * @param [packages] The name of the package to be installed.
 * @param [extraArgs] Additional arguments to pass to the Poetry add command.
 */
@Internal
suspend fun poetryInstallPackage(sdk: Sdk, packages: List<String>, extraArgs: List<String>): PyResult<String> {
  val args = listOf("add") + packages + extraArgs
  return runPoetryWithSdk(sdk, *args.toTypedArray())
}

@Internal
suspend fun poetryInstallPackageDetached(sdk: Sdk, packages: List<String>, extraArgs: List<String>): PyResult<String> {
  val args = listOf("run", "pip", "install") + packages + extraArgs
  return runPoetryWithSdk(sdk, *args.toTypedArray())
}

/**
 * Uninstalls a Python package using Poetry.
 * Runs `poetry remove [packages]`
 *
 * @param [packages] The name of the package to be uninstalled.
 */
@Internal
suspend fun poetryRemovePackage(sdk: Sdk, vararg packages: String): PyResult<String> = runPoetryWithSdk(sdk, "remove", *packages)

@Internal
suspend fun poetryUninstallPackage(sdk: Sdk, vararg packages: String): PyResult<String> {
  val args = listOf("run", "pip", "uninstall", "-y", *packages)
  return runPoetryWithSdk(sdk, *args.toTypedArray())
}

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
suspend fun poetryShowOutdated(sdk: Sdk): PyResult<Map<String, PythonOutdatedPackage>> {
  val output = runPoetryWithSdk(sdk, "show", "--all", "--outdated").getOr { return it }

  return parsePoetryShowOutdated(output).let { PyResult.success(it) }
}

@Internal
suspend fun poetryListPackages(sdk: Sdk): PyResult<Pair<List<PyPackage>, List<PyRequirement>>> {
  val version = getPoetryVersion()?.toVersion()

  // Ensure that the lock file is up to date.
  if (!Registry.get("python.poetry.list.packages.without.lock").asBoolean() && !checkLock(sdk, version)) {
    fixLock(sdk, version).getOr { return it }
  }

  val output = runPoetryWithSdk(sdk, "install", "--dry-run", "--no-root").getOr { return it }

  return parsePoetryInstallDryRun(output).let {
    PyResult.success(it)
  }
}

@Internal
suspend fun checkLock(sdk: Sdk, version: Version?): Boolean {
  // From Poetry 1.6.0 and forward, `poetry check --lock` should be used to figure out the validity of the lock file.
  // However, this command fails whenever a README file (as described in pyproject.toml) is absent, without even checking the lock file.
  // The old command, albeit deprecated, doesn't check for the README; instead, it only checks for the validity of the lock file.
  // After Poetry 2.0.0 and forward, `poetry check --lock` also only checks for the lock file, ignoring the existence of a README.
  if (version == null || version >= VERSION_2) {
    return runPoetryWithSdk(sdk, "check", "--lock").isSuccess
  }

  return runPoetryWithSdk(sdk, "lock", "--check").isSuccess
}

@Internal
suspend fun fixLock(sdk: Sdk, version: Version?): PyResult<String> {
  if (version == null || version >= VERSION_2) {
    return runPoetryWithSdk(sdk, "lock")
  }

  return runPoetryWithSdk(sdk, "lock", "--no-update")
}

@Internal
fun parsePoetryInstallDryRun(input: String): Pair<List<PyPackage>, List<PyRequirement>> {
  val installedLines = listOf("Already installed", "Skipping", "Updating", "Downgrading")

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
