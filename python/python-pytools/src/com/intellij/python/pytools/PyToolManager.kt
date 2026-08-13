// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.python.pytools.PyToolsBundle.message
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
   * Whether this tool can be installed onto [eelDescriptor]'s machine from the IDE. Default `true`
   * (uv/pip work against any target). An installer that only works locally — conda's Miniconda
   * installer — returns `false` for remote machines, so the settings row hides its Install action there.
   */
  fun canInstall(eelDescriptor: EelDescriptor): Boolean = true
}

/**
 * Default per-tool strategy: install/upgrade the tool as a Python package via the environment's
 * [GenericPyToolManager] (uv tool install, or a pip install into a system Python). Tools whose
 * [PyTool.manager] is this object are exactly the ones the generic uv/pip backend manages.
 */
object PackagePyToolManager : PyToolManager {
  override suspend fun install(tool: PyTool, eel: EelApi): PyResult<Path> =
    GenericPyToolManagerProvider.managerFor(eel)?.install(tool) ?: noInstaller(tool)

  override suspend fun upgrade(tool: PyTool, eel: EelApi): PyResult<Path> =
    GenericPyToolManagerProvider.managerFor(eel)?.upgrade(tool) ?: noInstaller(tool)

  private fun noInstaller(tool: PyTool): PyResult<Path> =
    PyResult.localizedError(message("python.tool.install.no.installer", tool.presentableName))
}
