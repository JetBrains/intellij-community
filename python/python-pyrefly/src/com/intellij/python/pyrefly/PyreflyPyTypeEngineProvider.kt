package com.intellij.python.pyrefly

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.stopAndRestartClientsIfNeeded
import com.intellij.platform.lsp.api.stopClients
import com.intellij.python.lsp.core.PyLspToolSettings
import com.intellij.python.lsp.core.isActiveOn
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProvider
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineType
import com.intellij.python.pyrefly.lsp.BundledPyreflyLspIntegrationProvider
import com.intellij.python.pyrefly.lsp.PyreflyLspIntegrationProvider
import com.intellij.python.pyrefly.typeEngine.PyreflyLspTypeEngineProvider

internal class PyreflyPyTypeEngineProvider : PyTypeEngineProvider {
  override val pyTypeEngineType: PyTypeEngineType
    get() = PyTypeEngineType.PYREFLY

  override fun runsOwnServer(project: Project): Boolean = PyreflyPyTool.isBundledPyreflyEnabled()

  /** The bundled server reads its own settings. Without it, the type engine uses the External Tools Pyrefly and its settings. */
  override fun featureSettings(project: Project): PyLspToolSettings =
    if (PyreflyPyTool.isBundledPyreflyEnabled()) PyreflyTypeEngineConfiguration.getInstance(project)
    else project.service<PyreflyConfiguration>()

  override fun isSupported(project: Project): Boolean {
    return PyreflyLspTypeEngineProvider.Util.isAvailable(project)
  }

  /**
   * Starts or stops both Pyrefly servers. Without `use.bundled.pyrefly` the type engine uses the External Tools
   * Pyrefly, so the selection changes that server too.
   *
   * `isSupported` is not required here. The tool runs in multi-module projects, where the engine does not, and a
   * selected engine is already supported.
   */
  override fun updateLspServers(project: Project) {
    val pyTool = PyreflyPyTool.getInstance()
    val lspServerManager = LspClientManager.getInstance(project)
    if (pyTool.isActiveOn(project)) {
      lspServerManager.stopAndRestartClientsIfNeeded<PyreflyLspIntegrationProvider>()
    }
    else {
      lspServerManager.stopClients<PyreflyLspIntegrationProvider>()
    }
    if (pyTool.runsBundledPyrefly(project)) {
      lspServerManager.stopAndRestartClientsIfNeeded<BundledPyreflyLspIntegrationProvider>()
    }
    else {
      lspServerManager.stopClients<BundledPyreflyLspIntegrationProvider>()
    }
  }
}
