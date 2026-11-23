// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyrefly

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.stopAndRestartClientsIfNeeded
import com.intellij.platform.lsp.api.stopClients
import com.intellij.python.lsp.core.PyLspTool
import com.intellij.python.lsp.core.PyLspToolIntegrationProvider
import com.intellij.python.lsp.core.isActiveOn
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProjectSettings
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineType
import com.intellij.python.pyrefly.lsp.BundledPyreflyLspIntegrationProvider
import com.intellij.python.pyrefly.lsp.PyreflyLspIntegrationProvider
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pytools.backend.PyTypeEngineSelectableTool
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * [Pyrefly](https://pyrefly.org/) — a fast Python type checker written in Rust by Meta, providing type
 * checking and IDE features through a language server.
 *
 * Pyrefly runs two servers, and each one has its own provider:
 * - The External Tools Pyrefly runs the `pyrefly` of the environment, see [PyreflyLspIntegrationProvider].
 *   The External Tools toggle controls it, see [isActiveOn].
 * - The bundled Pyrefly runs the binary of the plugin, see [BundledPyreflyLspIntegrationProvider]. The type
 *   engine selection controls it, see [runsBundledPyrefly].
 *
 * Without `use.bundled.pyrefly`, the type engine uses the External Tools Pyrefly.
 */
@ApiStatus.Internal
class PyreflyPyTool : PyLspTool<PyreflyConfiguration>(), PyTypeEngineSelectableTool {
  override val lspServerName: String = "Pyrefly"
  override val icon: Icon = PyreflyUtil.getDefaultPyreflyIcon()
  override val packageName: PyPackageName = PyPackageName.from("pyrefly")

  override fun configuration(project: Project): PyreflyConfiguration = project.service()

  override fun onEnabledChanged(project: Project, enabled: Boolean) {
    // Only the type engine that uses the External Tools Pyrefly depends on the toggle.
    if (!enabled && isSelectedAsTypeEngine(project) && !isBundledPyreflyEnabled()) {
      PyTypeEngineProjectSettings.getInstance(project).typeEngine = PyTypeEngineType.PYCHARM
    }
    val manager = LspClientManager.getInstance(project)
    if (isActiveOn(project) || runsForExternalTypeEngine(project)) manager.stopAndRestartClientsIfNeeded<PyreflyLspIntegrationProvider>()
    else manager.stopClients<PyreflyLspIntegrationProvider>()
  }

  override fun isSelectedAsTypeEngine(project: Project): Boolean =
    Registry.`is`("pyrefly.type.engine") ||
    PyTypeEngineProjectSettings.getInstance(project).typeEngine == PyTypeEngineType.PYREFLY

  override fun runsForExternalTypeEngine(project: Project): Boolean = isSelectedAsTypeEngine(project) && !isBundledPyreflyEnabled()

  /** Whether the bundled Pyrefly runs: Pyrefly is the selected type engine, and `use.bundled.pyrefly` is on. */
  fun runsBundledPyrefly(project: Project): Boolean = isSelectedAsTypeEngine(project) && isBundledPyreflyEnabled()

  companion object {
    fun isBundledPyreflyEnabled(): Boolean = Registry.`is`("use.bundled.pyrefly", false)

    fun getInstance(): PyreflyPyTool = PyTool.EP_NAME.findExtensionOrFail(PyreflyPyTool::class.java)

    /** The provider whose server the type engine reads its types from. */
    fun typeEngineProviderClass(): Class<out PyLspToolIntegrationProvider> =
      if (isBundledPyreflyEnabled()) BundledPyreflyLspIntegrationProvider::class.java
      else PyreflyLspIntegrationProvider::class.java
  }
}
