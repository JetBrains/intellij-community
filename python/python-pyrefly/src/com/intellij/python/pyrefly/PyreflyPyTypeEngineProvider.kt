package com.intellij.python.pyrefly

import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.stopAndRestartClientsIfNeeded
import com.intellij.platform.lsp.api.stopClients
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProvider
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineType
import com.intellij.python.pyrefly.lsp.PyreflyLspIntegrationProvider
import com.intellij.python.pyrefly.typeEngine.PyreflyLspTypeEngineProvider
import com.intellij.python.pytools.backend.isActiveOn

internal class PyreflyPyTypeEngineProvider : PyTypeEngineProvider {
  override val pyTypeEngineType: PyTypeEngineType
    get() = PyTypeEngineType.PYREFLY

  override fun isSupported(project: Project): Boolean {
    return PyreflyLspTypeEngineProvider.Util.isAvailable(project)
  }

  override fun updateLspServers(project: Project) {
    val lspServerManager = LspClientManager.getInstance(project)
    if (shouldBeEnabled(project)) {
      lspServerManager.stopAndRestartClientsIfNeeded<PyreflyLspIntegrationProvider>()
    }
    else {
      lspServerManager.stopClients<PyreflyLspIntegrationProvider>()
    }
  }

  private fun shouldBeEnabled(project: Project): Boolean {
    // Run the server whenever Pyrefly is active — either selected as the type engine, or enabled as
    // an LSP tool. `isSupported` is intentionally not required here: the tool is allowed in
    // multi-module projects (where the engine is not), and a selected engine is already supported.
    return PyreflyPyTool.getInstance().isActiveOn(project)
  }
}
