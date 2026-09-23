// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.backend

import com.jetbrains.python.errorProcessing.PyResult
import java.nio.file.Path

/**
 * Installs and upgrades [PyTool] executables in a single environment. An instance is bound to that
 * environment (a project's EEL) by the [GenericPyToolManagerProvider] that created it, so [install] / [upgrade]
 * take no `eel` argument.
 *
 * Backends (uv, pip, …) provide managers via [GenericPyToolManagerProvider]; a caller that needs the backend of one
 * particular installation obtains it with [GenericPyToolManagerProvider.managerOf].
 */
interface GenericPyToolManager {
  /** Installs [tool]; returns the resolved executable path. */
  suspend fun install(tool: PyTool): PyResult<Path>

  /** Upgrades [tool] to the latest version. */
  suspend fun upgrade(tool: PyTool): PyResult<Path>

  /**
   * What this backend knows about those of [tools] it manages — each one's executable, installed version and latest
   * available version. A tool this backend does not manage is simply absent from the result.
   *
   * The caller asks the machine's backends in order and narrows [tools] to what the earlier ones left uncovered, so
   * a backend that has to reach the package repository is asked only about tools no cheaper backend claimed. That is
   * also why a backend must not report a tool it does not manage: the next one would never be asked, and an upgrade
   * would go to a backend that leaves the resolved executable untouched.
   */
  suspend fun list(tools: Collection<PyTool>): Map<PyTool, InstalledInfo>
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
