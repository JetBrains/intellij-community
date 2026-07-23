// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.execution.Platform
import com.intellij.openapi.components.service
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.execService.DownloadConfig
import com.intellij.python.community.execService.UploadConfig
import com.intellij.python.community.execService.python.validatePythonAndGetInfo
import com.intellij.python.community.impl.poetry.common.poetryPath
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.getOrNull
import com.jetbrains.python.isSuccess
import com.jetbrains.python.onFailure
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.sdk.ToolCommandExecutor
import com.jetbrains.python.sdk.ToolSearchPath
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
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.toVersion
import org.apache.tuweni.toml.Toml
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 *  This source code is edited by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */
private val VERSION_2 = "2.0.0".toVersion()


internal val POETRY_TOOL: ToolCommandExecutor = ToolCommandExecutor(
  "poetry",
  // TODO: Poetry from store isn't detected because local eel doesn't obey appx binaries. We need to fix it on eel side
  additionalSearchPaths = listOf(ToolSearchPath.RelativePathFromHome(listOf(".poetry", ".bin"), Platform.WINDOWS)),
  getToolPathFromSettings = { poetryPath }
)

private val POETRY_EXCLUDE_NON_DIGITS_REGEX = Regex("""\D+$""")
private const val POETRY_TOML = "poetry.toml"
private const val POETRY_LOCK = "poetry.lock"
private val POETRY_PROJECT_FILES = listOf(PY_PROJECT_TOML, POETRY_LOCK, POETRY_TOML)
private val POETRY_PROJECT_DOWNLOAD_CONFIG = DownloadConfig(relativePaths = POETRY_PROJECT_FILES)
private val POETRY_PROJECT_MUTATING_COMMANDS = setOf("add", "config", "init", "install", "lock", "new", "remove", "update")

private fun <P : PathHolder> Path.createPoetryMetadataUploadConfig(fileSystem: FileSystem<P>): UploadConfig? =
  if (fileSystem.isLocal) null
  else UploadConfig(relativePaths = POETRY_PROJECT_FILES.filter { resolve(it).isRegularFile() })

@Internal
internal suspend fun <P : PathHolder> runPoetry(
  fileSystem: FileSystem<P>,
  projectPath: Path?,
  vararg args: String,
  poetryExecutable: P? = null,
  inProjectEnv: Boolean? = null,
  baseEnv: Map<String, String> = emptyMap(),
  uploadConfig: UploadConfig? = null,
  downloadConfig: DownloadConfig? = null,
): PyResult<String> {
  val env = baseEnv.toMutableMap().apply {
    if (inProjectEnv != null) put("POETRY_VIRTUALENVS_IN_PROJECT", inProjectEnv.toString())
  }
  return POETRY_TOOL.runTool(
    fileSystem = fileSystem,
    pathFromSdk = poetryExecutable?.toString(),
    dirPath = projectPath,
    args = args,
    env = env,
    uploadConfig = uploadConfig,
    downloadConfig = downloadConfig,
  )
}

@Internal
internal suspend fun runPoetry(
  projectPath: Path?,
  vararg args: String,
  inProjectEnv: Boolean? = null,
  baseEnv: Map<String, String> = emptyMap(),
): PyResult<String> {
  return runPoetry(
    fileSystem = projectPath.toEelFileSystem(),
    projectPath = projectPath,
    args = args,
    inProjectEnv = inProjectEnv,
    baseEnv = baseEnv,
  )
}


/**
 * Returns the configured poetry executable or detects it automatically on the given [fileSystem].
 */
@Internal
internal suspend fun <P : PathHolder> getPoetryExecutable(fileSystem: FileSystem<P>): P? =
  POETRY_TOOL.getToolExecutable(fileSystem, pathFromSdk = null)

/**
 * Returns the configured poetry executable or detects it automatically.
 */
@Internal
internal suspend fun getPoetryExecutable(eel: EelApi = localEel): Path? =
  getPoetryExecutable(EelFileSystem(eel))?.path

/**
 * Runs poetry command for the specified Poetry SDK.
 * Runs:
 * 1. `poetry env use [sdk]`
 * 2. `poetry [args]`
 */
@Internal
internal suspend fun runPoetryWithSdk(sdk: Sdk, vararg args: String): PyResult<String> {
  val data = sdk.pySdkAdditionalData
  val projectPath = data.workingDirectory.takeIf { data.hasValidWorkingDirectory() }
                    ?: return PyResult.localizedError(PyBundle.message("python.sdk.project.working.directory.not.found"))
  val sdkHomePath = sdk.homePath
                    ?: return PyResult.localizedError(PySdkBundle.message("python.sdk.broken.configuration", sdk.name))

  return when (data) {
    is PyTargetAwareAdditionalData -> {
      val targetConfig = data.targetEnvironmentConfiguration
                         ?: return PyResult.localizedError(PySdkBundle.message("python.sdk.broken.configuration", sdk.name))
      val targetFileSystemCache = service<TargetFileSystemCache>()
      runPoetryWithSdk(
        fileSystem = targetFileSystemCache.getOrCreate(targetConfig, PythonLanguageRuntimeConfiguration()),
        projectPath = projectPath,
        sdkHomePath = sdkHomePath,
        args = args,
      )
    }
    else -> runPoetryWithSdk(
      fileSystem = projectPath.toEelFileSystem(),
      projectPath = projectPath,
      sdkHomePath = sdkHomePath,
      args = args,
    )
  }
}

private suspend fun <P : PathHolder> runPoetryWithSdk(
  fileSystem: FileSystem<P>,
  projectPath: Path,
  sdkHomePath: String,
  vararg args: String,
): PyResult<String> {
  val pythonPath = fileSystem.parsePath(sdkHomePath).getOr { return it }
  val pythonHomePath = fileSystem.resolvePythonHome(pythonPath)
  val env = buildMap {
    put("POETRY_VIRTUALENVS_IN_PROJECT", "false")
    put("POETRY_VIRTUALENVS_PREFER_ACTIVE_PYTHON", "true")
    put("VIRTUAL_ENV", pythonHomePath.toString())
  }
  return runPoetry(
    fileSystem = fileSystem,
    projectPath = projectPath,
    args = args,
    baseEnv = env,
    downloadConfig = POETRY_PROJECT_DOWNLOAD_CONFIG.takeIf { args.firstOrNull() in POETRY_PROJECT_MUTATING_COMMANDS },
  )
}


/**
 * Sets up the poetry environment for the specified project path.
 *
 * @return the path to the poetry environment.
 */
@Internal
internal suspend fun <P : PathHolder> setupPoetry(
  projectPath: Path,
  fileSystem: FileSystem<P>,
  poetryExecutable: P?,
  basePythonBinaryPath: P,
  installPackages: Boolean,
  init: Boolean,
  errorSink: ErrorSink,
  inProjectEnv: Boolean = false,
): PyResult<P> {
  if (init) {
    // Build poetry init command with Python version constraint if available
    val initArgs = mutableListOf("init", "-n")

    val projectName = PyPackageName.normalizeProjectName(projectPath.name)
    if (projectName.isNotBlank()) {
      initArgs.add("--name")
      initArgs.add(projectName)
    }

    // Validate Python and get version info
    val pythonInfo = fileSystem.getBinaryToExec(basePythonBinaryPath).validatePythonAndGetInfo().getOr { return it }
    val major = pythonInfo.languageLevel.majorVersion
    val minor = pythonInfo.languageLevel.minorVersion
    // Add --python flag with caret constraint (e.g., "^3.10")
    initArgs.add("--python")
    initArgs.add("^$major.$minor")

    runPoetry(
      fileSystem = fileSystem,
      projectPath = projectPath,
      args = initArgs.toTypedArray(),
      poetryExecutable = poetryExecutable,
      inProjectEnv = inProjectEnv,
      downloadConfig = POETRY_PROJECT_DOWNLOAD_CONFIG,
    ).getOr { return it }
  }

  runPoetry(
    fileSystem = fileSystem,
    projectPath = projectPath,
    "env", "use", basePythonBinaryPath.toString(),
    poetryExecutable = poetryExecutable,
    inProjectEnv = inProjectEnv,
  ).getOr { return it }

  if (installPackages) {
    runPoetry(
      fileSystem = fileSystem,
      projectPath = projectPath,
      "install", "--no-root",
      poetryExecutable = poetryExecutable,
      inProjectEnv = inProjectEnv,
      downloadConfig = POETRY_PROJECT_DOWNLOAD_CONFIG,
    ).onFailure { errorSink.emit(it) }
  }

  val pythonHomePath = runPoetry(
    fileSystem = fileSystem,
    projectPath = projectPath,
    "env", "info", "-p",
    poetryExecutable = poetryExecutable,
    inProjectEnv = inProjectEnv,
  ).getOr { return it }
  return fileSystem.parsePath(pythonHomePath.trim())
}

@Internal
internal suspend fun setupPoetry(
  projectPath: Path,
  basePythonBinaryPath: PythonBinary,
  installPackages: Boolean,
  init: Boolean,
  errorSink: ErrorSink,
  inProjectEnv: Boolean = false,
): PyResult<PythonHomePath> {
  val fileSystem = projectPath.toEelFileSystem()
  return setupPoetry(
    projectPath = projectPath,
    fileSystem = fileSystem,
    poetryExecutable = null,
    basePythonBinaryPath = PathHolder.Eel(basePythonBinaryPath),
    installPackages = installPackages,
    init = init,
    errorSink = errorSink,
    inProjectEnv = inProjectEnv,
  ).mapSuccess { it.path }
}

internal suspend fun <P : PathHolder> detectPoetryEnvs(
  searchPath: Path,
  fileSystem: FileSystem<P>,
  poetryExecutable: P? = null,
): List<P> = getPoetryEnvs(searchPath, fileSystem, poetryExecutable).mapNotNull { homePath ->
  fileSystem.parsePath(homePath).successOrNull?.let { fileSystem.resolvePythonBinary(it) }
}

private suspend fun getPoetryVersion(sdk: Sdk): String? =
  runPoetryWithSdk(sdk, "--version").getOrNull()?.parsePoetryVersion()

private fun String.parsePoetryVersion(): String? =
  split(' ').lastOrNull()?.replace(POETRY_EXCLUDE_NON_DIGITS_REGEX, "")

/**
 * Installs a Python package using Poetry.
 * Runs `poetry add [packages] [extraArgs]`
 *
 * @param [packages] The name of the package to be installed.
 * @param [extraArgs] Additional arguments to pass to the Poetry add command.
 */
@Internal
internal suspend fun poetryInstallPackage(sdk: Sdk, packages: List<String>, extraArgs: List<String>): PyResult<String> {
  val args = listOf("add") + packages + extraArgs
  return runPoetryWithSdk(sdk, *args.toTypedArray())
}

@Internal
internal suspend fun poetryInstallPackageDetached(sdk: Sdk, packages: List<String>, extraArgs: List<String>): PyResult<String> {
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
internal suspend fun poetryRemovePackage(sdk: Sdk, vararg packages: String): PyResult<String> = runPoetryWithSdk(sdk, "remove", *packages)

@Internal
suspend fun poetryUninstallPackage(sdk: Sdk, vararg packages: String): PyResult<String> {
  val args = listOf("run", "pip", "uninstall", "-y", *packages)
  return runPoetryWithSdk(sdk, *args.toTypedArray())
}

@Internal
internal fun parsePoetryShow(input: String): List<PythonPackage> {
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
internal suspend fun poetryShowOutdated(sdk: Sdk): PyResult<Map<String, PythonOutdatedPackage>> {
  val output = runPoetryWithSdk(sdk, "show", "--all", "--outdated").getOr { return it }

  return parsePoetryShowOutdated(output).let { PyResult.success(it) }
}

@Internal
internal suspend fun poetryListPackages(sdk: Sdk): PyResult<Pair<List<PyPackage>, List<PyRequirement>>> {
  val version = getPoetryVersion(sdk)?.toVersion()

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
internal suspend fun checkLock(sdk: Sdk, version: Version?): Boolean {
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
internal suspend fun fixLock(sdk: Sdk, version: Version?): PyResult<String> {
  if (version == null || version >= VERSION_2) {
    return runPoetryWithSdk(sdk, "lock")
  }

  return runPoetryWithSdk(sdk, "lock", "--no-update")
}

@Internal
internal fun parsePoetryInstallDryRun(input: String): Pair<List<PyPackage>, List<PyRequirement>> {
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
 * Parses `poetry.lock` and returns a map of editable package names to their source URLs.
 * A package is editable when `develop = true` in its `[[package]]` entry.
 */
internal fun parsePoetryLockEditablePackages(lockContent: String): Map<String, String?> {
  val packages = Toml.parse(lockContent).getArray("package") ?: return emptyMap()
  return buildMap {
    for (i in 0 until packages.size()) {
      val pkg = packages.getTable(i)
      if (pkg.getBoolean("develop") == true) {
        val name = pkg.getString("name") ?: continue
        put(name, pkg.getTable("source")?.getString("url"))
      }
    }
  }
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
internal suspend fun <P : PathHolder> configurePoetryEnvironment(
  modulePath: Path?,
  fileSystem: FileSystem<P>,
  poetryExecutable: P? = null,
  vararg args: String,
) {
  runPoetry(
    fileSystem = fileSystem,
    projectPath = modulePath,
    "config", *args,
    poetryExecutable = poetryExecutable,
    downloadConfig = POETRY_PROJECT_DOWNLOAD_CONFIG,
  )
}

internal suspend fun configurePoetryEnvironment(modulePath: Path?, vararg args: String) {
  configurePoetryEnvironment(modulePath, modulePath.toEelFileSystem(), args = args)
}

private suspend fun <P : PathHolder> getPoetryEnvs(
  projectPath: Path,
  fileSystem: FileSystem<P>,
  poetryExecutable: P?,
): List<String> {
  val executionResult = runPoetry(
    fileSystem = fileSystem,
    projectPath = projectPath,
    "env", "list", "--full-path",
    poetryExecutable = poetryExecutable,
    uploadConfig = projectPath.createPoetryMetadataUploadConfig(fileSystem),
  )
  return executionResult.getOrNull()?.lineSequence()?.map { it.split(" ")[0] }?.filterNot { it.isEmpty() }?.toList() ?: emptyList()
}
