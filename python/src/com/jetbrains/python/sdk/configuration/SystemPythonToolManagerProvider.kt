// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration

import com.intellij.platform.eel.EelApi
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.services.systemPython.SystemPython
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.pytools.InstalledInfo
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.PyToolManager
import com.intellij.python.pytools.PyToolManagerProvider
import com.intellij.python.pytools.configuration.ConfigurablePyTool
import com.intellij.python.pytools.getToolVersion
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.packaging.PyPackageVersionNormalizer
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.toFileSystem
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.installExecutableViaPythonScript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Terminal [PyToolManagerProvider] fallback: yields a manager that pip-installs into the first system
 * Python of the target environment. Registered last, so it is used only when no higher-priority
 * provider (e.g. uv) can operate there.
 */
@ApiStatus.Internal
class SystemPythonToolManagerProvider : PyToolManagerProvider {
  override suspend fun forEel(eel: EelApi): PyToolManager? {
    val systemPython = SystemPythonService().findSystemPythons(eelApi = eel).firstOrNull() ?: return null
    return SystemPythonToolManager(eel.toFileSystem(), systemPython)
  }
}

/** pip-installs tools into [systemPython] via the `pycharm_package_installer.py` helper. */
private class SystemPythonToolManager(
  private val fileSystem: FileSystem<PathHolder.Eel>,
  private val systemPython: SystemPython,
) : PyToolManager {
  override suspend fun install(tool: PyTool): PyResult<Path> {
    installExecutableViaPythonScript(systemPython.asExecutablePython.binary, "-n", tool.packageName.name).getOr { return it }
    val executable = fileSystem.detectTool(tool.packageName.name)
                     ?: return PyResult.localizedError(PySdkBundle.message("cannot.find.executable", tool.packageName.name, fileSystem.userReadableName))
    return Result.success(executable.path)
  }

  /** The pip helper always installs the latest release, so an upgrade is just a fresh install. */
  override suspend fun upgrade(tool: PyTool): PyResult<Path> = install(tool)

  /**
   * Every configurable tool that is actually installed (resolved on [fileSystem]), with its `--version`
   * probed and the latest release looked up from PyPI. When PyPI is unreachable the latest version falls
   * back to the installed one (i.e. reported as up to date).
   */
  override suspend fun list(): Map<PyTool, InstalledInfo> {
    return PyTool.EP_NAME.extensionList.filter { it is ConfigurablePyTool }.mapNotNull { tool ->
      val name = tool.packageName.name
      val executable = fileSystem.detectTool(name) ?: return@mapNotNull null
      val installed = BinOnEel(executable.path).getToolVersion(name).getOrNull()?.value ?: return@mapNotNull null
      val latest = latestPyPiVersion(name) ?: installed
      tool to InstalledInfo(path = executable.path, installedVersion = installed, latestVersion = latest)
    }.toMap()
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
