// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools

import com.jetbrains.python.errorProcessing.PyResult
import java.nio.file.Path

/**
 * Installs and upgrades [PyTool] executables in a single environment. An instance is bound to that
 * environment (a project's EEL) by the [PyToolManagerProvider] that created it, so [install] / [upgrade]
 * take no `eel` argument.
 *
 * Backends (uv, pip, …) provide managers via [PyToolManagerProvider]; callers obtain one with
 * [PyToolManagerProvider.managerFor] and use it for every operation.
 */
interface PyToolManager {
  /** Installs [tool]; returns the resolved executable path. */
  suspend fun install(tool: PyTool): PyResult<Path>

  /** Upgrades [tool] to the latest version. */
  suspend fun upgrade(tool: PyTool): PyResult<Path>

  /**
   * All managed tools installed in this manager's environment, keyed by [PyTool], with their installed
   * and latest available version (the latest resolved from PyPI). Empty when no managed tool is installed.
   */
  suspend fun list(): Map<PyTool, InstalledInfo>
}

/**
 * Version and location of an installed managed tool: its resolved executable [path], the currently
 * [installedVersion], and the [latestVersion] available from the configured repositories (equal to
 * [installedVersion] when the tool is already up to date).
 */
data class InstalledInfo(
  val path: Path,
  val installedVersion: String,
  val latestVersion: String,
)
