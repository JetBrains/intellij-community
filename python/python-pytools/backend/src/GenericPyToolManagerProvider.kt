// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.backend

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelApi

/**
 * Creates a [GenericPyToolManager] for a given environment, contributed per backend (e.g. uv, pip) through
 * [EP_NAME]. Providers are consulted in registration order; the first one able to operate in the
 * target environment wins.
 *
 * Implementations live in higher-level modules (uv backend, the main python impl) so `python-pytools`
 * does not need to depend on them.
 */
interface GenericPyToolManagerProvider {
  /**
   * A [GenericPyToolManager] bound to [eel], or `null` when this provider cannot operate there — e.g. its
   * backing tool (uv on PATH, a system Python, …) is not present.
   */
  suspend fun forEel(eel: EelApi): GenericPyToolManager?

  companion object {
    val EP_NAME: ExtensionPointName<GenericPyToolManagerProvider> =
      ExtensionPointName.create("com.intellij.python.pytools.genericPyToolManagerProvider")

    /**
     * Every backend that can operate in [eel], in provider order.
     *
     * The first is the one to install a tool the machine does not have yet, where the question is "who can place
     * this" and nothing manages the tool yet. For a tool that is already installed, ask [managerOf]: the first
     * backend is not the one managing every installation on the machine.
     */
    suspend fun managersFor(eel: EelApi): List<GenericPyToolManager> = EP_NAME.extensionList.mapNotNull { it.forEel(eel) }

    /**
     * Everything the machine's backends know about [tools], each backend asked only about what the ones before it
     * left uncovered, and none asked at all once nothing is left.
     */
    suspend fun listAll(eel: EelApi, tools: Collection<PyTool>): Map<PyTool, InstalledInfo> =
      managersFor(eel).fold(emptyMap()) { covered, manager ->
        val remaining = tools - covered.keys
        if (remaining.isEmpty()) covered else covered + manager.list(remaining)
      }

    /** The backend that manages [tool]'s installation in [eel], or `null` when none does. */
    suspend fun managerOf(eel: EelApi, tool: PyTool): GenericPyToolManager? =
      managersFor(eel).firstOrNull { it.list(listOf(tool)).isNotEmpty() }
  }
}
