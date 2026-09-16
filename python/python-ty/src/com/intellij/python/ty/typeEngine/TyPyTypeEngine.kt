package com.intellij.python.ty.typeEngine

import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.stopAndRestartClientsIfNeeded
import com.intellij.platform.lsp.api.stopClients
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProvider
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineType
import com.intellij.python.pytools.backend.isActiveOn
import com.intellij.python.ty.TyLspIntegrationProvider
import com.intellij.python.ty.TyPyTool

internal class TyPyTypeEngine : PyTypeEngineProvider {
  override val pyTypeEngineType: PyTypeEngineType
    get() = PyTypeEngineType.TY

  override fun isSupported(project: Project): Boolean {
    return isTyTypeEngineFeatureEnabled(project)
  }

  override fun updateLspServers(project: Project) {
    val lspServerManager = LspClientManager.getInstance(project)
    if (shouldBeEnabled(project)) {
      lspServerManager.stopAndRestartClientsIfNeeded<TyLspIntegrationProvider>()
    }
    else {
      lspServerManager.stopClients<TyLspIntegrationProvider>()
    }
  }

  private fun shouldBeEnabled(project: Project): Boolean {
    // Run the server whenever ty is active — selected as the type engine, or enabled as an LSP tool.
    return TyPyTool.getInstance().isActiveOn(project)
  }
}
