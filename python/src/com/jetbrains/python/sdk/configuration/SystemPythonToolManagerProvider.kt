// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.services.systemPython.SystemPython
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.pytools.backend.InstalledInfo
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pytools.backend.GenericPyToolManager
import com.intellij.python.pytools.backend.GenericPyToolManagerProvider
import com.intellij.python.pytools.backend.PyToolsBundle
import com.intellij.python.pytools.backend.getToolVersion
import com.intellij.python.requirements.PyPackageVersionNormalizer
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import com.intellij.python.sdk.backend.detectExecutableInPath
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.toFileSystem
import com.jetbrains.python.sdk.installExecutableViaPythonScript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isExecutable

/**
 * Terminal [GenericPyToolManagerProvider] fallback: yields a manager that pip-installs into the first system
 * Python of the target environment. Registered last, so it is used only when no higher-priority
 * provider (e.g. uv) can operate there.
 */
@ApiStatus.Internal
class SystemPythonToolManagerProvider : GenericPyToolManagerProvider {
  override suspend fun forEel(eel: EelApi): GenericPyToolManager? {
    val systemPython = SystemPythonService().findSystemPythons(eelApi = eel).firstOrNull() ?: return null
    return SystemPythonToolManager(eel.descriptor, eel.toFileSystem(), systemPython)
  }
}

/** pip-installs tools into [systemPython] via the `pycharm_package_installer.py` helper. */
private class SystemPythonToolManager(
  private val eelDescriptor: EelDescriptor,
  private val fileSystem: FileSystem<PathHolder.Eel>,
  private val systemPython: SystemPython,
) : GenericPyToolManager {
  override suspend fun install(tool: PyTool): PyResult<Path> {
    // The pip helper drops the launcher into a per-user scripts directory that is frequently not on PATH
    // (e.g. %APPDATA%\Python\Scripts on Windows), so trust the path it reports rather than re-detecting the
    // tool on PATH, which would spuriously fail with "cannot find executable" (PY-91493).
    return installExecutableViaPythonScript(systemPython.asExecutablePython.binary, "-n", tool.packageName.name)
  }

  /**
   * The pip helper always installs the latest release, so an upgrade is a fresh install — but into the environment
   * that owns the executable, not the machine's first Python. Installing into another interpreter would leave the
   * resolved executable untouched and put a second, newer copy elsewhere: the row reports success and nothing the
   * IDE runs has changed.
   *
   * [list] claims a tool only when that environment can be identified, so the owner is found here in practice; an
   * upgrade asked for outside that route fails rather than installing into a guessed interpreter.
   */
  override suspend fun upgrade(tool: PyTool): PyResult<Path> {
    val name = tool.packageName.name
    val owner = fileSystem.detectExecutableInPath(name)?.path?.let { owningInterpreter(it) }
                ?: return PyResult.localizedError(PyToolsBundle.message("python.tool.upgrade.no.owner", name))
    return installExecutableViaPythonScript(BinOnEel(owner), "-n", name)
  }

  /**
   * Those of [tools] that resolve on [fileSystem], each with its `--version` probed and its latest release looked up
   * from PyPI. When PyPI is unreachable the latest version falls back to the installed one, i.e. the tool is
   * reported as up to date.
   *
   * This backend is asked last, so [tools] holds what the ones before it disowned — uv itself when uv cannot update
   * itself, and tools pip placed on a machine that also has uv. Only a tool that is actually installed costs a PyPI
   * request; an absent one stops at the path lookup.
   */
  override suspend fun list(tools: Collection<PyTool>): Map<PyTool, InstalledInfo> =
    tools.mapNotNull { tool -> info(tool)?.let { tool to it } }.toMap()

  private suspend fun info(tool: PyTool): InstalledInfo? {
    // Nothing to price for a tool the IDE cannot upgrade anyway (conda, whose own installer owns it).
    if (tool.manager?.support(eelDescriptor)?.canUpgrade != true) return null
    val name = tool.packageName.name
    // Resolve on PATH and in the per-user scripts dirs the pip helper installs into (e.g.
    // %APPDATA%\Python\Scripts on Windows), which are frequently not on PATH (PY-91493).
    val executable = fileSystem.detectExecutableInPath(name) ?: return null
    // Claim it only when the environment that owns it can be identified. A tool placed by Homebrew, apt or cargo
    // resolves here too, and pip cannot upgrade one of those: it would install a second copy into some interpreter
    // and leave this executable stale. Checked before the version and PyPI lookups, which it also saves.
    if (owningInterpreter(executable.path) == null) return null
    val installed = BinOnEel(executable.path).getToolVersion(name).getOrNull()?.value ?: return null
    val latest = latestPyPiVersion(name) ?: installed
    return InstalledInfo(path = executable.path, installedVersion = installed, latestVersion = latest)
  }

  /**
   * Latest stable release of [packageName] from PyPI, or `null` if it can't be determined. Queried
   * app-level through [PyPiPackageRepository] (no project needed); `availableVersions` come back sorted
   * newest-first, and we skip pre-/dev-releases to match the default "stable only" upgrade policy.
   */
  private suspend fun latestPyPiVersion(packageName: String): String? {
    val details = withContext(Dispatchers.IO) { PyPiPackageRepository.buildPackageDetails(packageName) }.getOrNull()
                  ?: return null
    return details.availableVersions.firstOrNull { version ->
      val normalized = PyPackageVersionNormalizer.normalize(version)
      normalized == null || (normalized.pre == null && normalized.dev == null)
    }
  }
}

/**
 * The interpreter whose environment owns [executable]: the one its launcher names in a shebang, else the one beside
 * it in a virtual environment. `null` when neither applies, rather than a guess — a wrong answer here upgrades
 * something other than what the row shows.
 */
internal suspend fun owningInterpreter(executable: Path): Path? = withContext(Dispatchers.IO) {
  shebangInterpreter(executable) ?: virtualEnvInterpreter(executable)
}

/**
 * The interpreter named by a pip console script's `#!` line. This is what identifies the owner of a
 * `pip install --user` launcher, which sits in a shared scripts directory with no interpreter beside it — the very
 * layout the pip helper creates.
 *
 * A shebang naming something that is not a Python is rejected: a launcher can be a shell wrapper
 * (`#!/bin/sh`, as `~/.local/bin/pipenv` is), and handing `/bin/sh` to the pip helper as an interpreter would fail
 * or install somewhere unintended.
 */
private fun shebangInterpreter(executable: Path): Path? {
  val head = try {
    Files.newInputStream(executable).use { stream ->
      val buffer = ByteArray(SHEBANG_PROBE_BYTES)
      val read = stream.read(buffer)
      if (read <= 0) return null
      String(buffer, 0, read, Charsets.UTF_8)
    }
  }
  catch (e: IOException) {
    return null
  }
  if (!head.startsWith("#!")) return null
  val named = try {
    Path.of(head.lineSequence().first().removePrefix("#!").trim())
  }
  catch (e: InvalidPathException) {
    return null
  }
  // `#!/usr/bin/env python3` names no interpreter, and a shell wrapper names one the helper cannot use.
  if (!named.isAbsolute || !named.isPython()) return null
  return named.takeIf { it.isExecutable() }
}

/**
 * The interpreter beside [executable] in a virtual environment — `<env>/bin/<tool>` next to `<env>/bin/python`.
 * Requires the `pyvenv.cfg` that marks the environment, so a tool merely sharing a directory with some interpreter
 * (`/opt/homebrew/bin`) is not mistaken for one pip owns. Symlinks are followed first, which is what leads a pipx
 * launcher to the environment it really lives in.
 */
private fun virtualEnvInterpreter(executable: Path): Path? {
  val real = try {
    executable.toRealPath()
  }
  catch (e: IOException) {
    executable
  }
  val environment = real.parent?.parent ?: return null
  if (!environment.resolve(PYVENV_CFG).exists()) return null
  return INTERPRETER_NAMES.firstNotNullOfOrNull { name -> real.resolveSibling(name).takeIf { it.isExecutable() } }
}

/** Whether the file name looks like a Python interpreter (`python`, `python3`, `python3.13`, `python.exe`). */
private fun Path.isPython(): Boolean = fileName?.toString()?.substringBefore(".exe")?.startsWith("python") == true

/** Enough of a launcher to hold its `#!` line; a pip console script's is the first thing in the file. */
private const val SHEBANG_PROBE_BYTES: Int = 256

/** The file a virtual environment is recognized by. */
private const val PYVENV_CFG: String = "pyvenv.cfg"

/** Interpreter file names to look for inside a virtual environment, most specific first. */
private val INTERPRETER_NAMES: List<String> = listOf("python3", "python", "python.exe")
