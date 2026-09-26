// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.backend

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.python.pytools.backend.PyToolsBundle.message
import com.jetbrains.python.errorProcessing.PyResult
import java.nio.file.Path

/**
 * Per-tool install/upgrade strategy. Each [PyTool] exposes one via [PyTool.manager]; a `null` manager
 * means the tool cannot be installed through the IDE (its settings row only lets the user point at an
 * existing executable, no Install/Upgrade actions).
 *
 * The default, [PackagePyToolManager], installs the tool as a Python package through whichever
 * [GenericPyToolManager] the environment offers (uv, else pip). Tools installed a different way — conda,
 * via its own installer — provide their own implementation.
 */
interface PyToolManager {
  /** Installs [tool] into the environment described by [eel]; returns the resolved executable path. */
  suspend fun install(tool: PyTool, eel: EelApi): PyResult<Path>

  /** Upgrades [tool] to the latest version in the environment described by [eel]. */
  suspend fun upgrade(tool: PyTool, eel: EelApi): PyResult<Path>

  /**
   * What the IDE can do with this tool on [eelDescriptor]'s machine. Default [PyToolSupport.INSTALL_AND_UPGRADE]
   * (uv/pip work against any target, and always install the newest release).
   *
   * An installer that only works locally — conda's Miniconda installer — reports [PyToolSupport.NONE] for a remote
   * machine, so the settings row there offers no action and only lets the user point at an executable.
   */
  fun support(eelDescriptor: EelDescriptor): PyToolSupport = PyToolSupport.INSTALL_AND_UPGRADE
}

/**
 * What the IDE can do with a tool on one machine. A single value rather than a flag per operation, because "upgrade
 * but not install" is not a state any tool is in, and a row that offers an upgrade it cannot perform is worse than
 * one that offers nothing.
 */
enum class PyToolSupport {
  /** Neither: the row only lets the user point at an existing executable. */
  NONE,

  /** Install, but not upgrade — the conda distribution, whose own installer owns updates. */
  INSTALL_ONLY,

  /** Both. */
  INSTALL_AND_UPGRADE,
  ;

  val canInstall: Boolean
    get() = when (this) {
      NONE -> false
      INSTALL_ONLY, INSTALL_AND_UPGRADE -> true
    }

  val canUpgrade: Boolean
    get() = when (this) {
      NONE, INSTALL_ONLY -> false
      INSTALL_AND_UPGRADE -> true
    }
}

/**
 * Default per-tool strategy: install/upgrade the tool as a Python package via the environment's
 * [GenericPyToolManager] (uv tool install, or a pip install into a system Python). Tools whose
 * [PyTool.manager] is this object are exactly the ones the generic uv/pip backend manages.
 */
object PackagePyToolManager : PyToolManager {
  override suspend fun install(tool: PyTool, eel: EelApi): PyResult<Path> =
    GenericPyToolManagerProvider.managersFor(eel).firstOrNull()?.install(tool) ?: noInstaller(tool)

  /**
   * Upgrades through the backend that manages this installation rather than the machine's highest-priority one: a
   * tool pip placed on a machine that also has uv is upgraded by pip, because `uv tool install` would leave the
   * resolved executable alone and put a second copy in uv's own bin directory.
   */
  override suspend fun upgrade(tool: PyTool, eel: EelApi): PyResult<Path> =
    GenericPyToolManagerProvider.managerOf(eel, tool)?.upgrade(tool) ?: noInstaller(tool)

  private fun noInstaller(tool: PyTool): PyResult<Path> =
    PyResult.localizedError(message("python.tool.install.no.installer", tool.packageName.name))
}
