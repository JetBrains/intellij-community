package com.intellij.python.pyrefly.typeEngine

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.ensureClientStarted
import com.intellij.platform.lsp.api.getClients
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineUtils
import com.intellij.python.pyrefly.PyreflyPyTool
import com.intellij.python.pyrefly.lsp.PyreflyLspClientDescriptor
import com.intellij.python.pyrefly.lsp.PyreflyLspIntegrationProvider
import com.intellij.python.pytools.isEnabledOn
import com.jetbrains.python.psi.types.engine.PyTypeEngine
import com.jetbrains.python.psi.types.engine.PyTypeEngineProvider


/**
 * External type engine provider that delegates to Pyrefly's LSP endpoint.
 * This provider is enabled when the Type Engine feature is enabled via registry.
 * The actual per-module check for Pyrefly configuration is done in [PyreflyLspTypeEngine.isSupportedForResolve].
 */
class PyreflyLspTypeEngineProvider : PyTypeEngineProvider {
  override fun createTypeEngine(module: Module): PyTypeEngine? {
    // Check if type engine feature is enabled (via registry or unit tests)
    val isFeatureEnabled = Util.isAvailable(module.project)

    if (!isFeatureEnabled) {
      return null
    }

    if (!PyreflyPyTool.getInstance().isEnabledOn(module.project)) {
      return null
    }
    val lspServerManager = LspClientManager.getInstance(module.project)
    lspServerManager.ensureClientStarted<PyreflyLspIntegrationProvider>(PyreflyLspClientDescriptor(module))
    val server = lspServerManager.getClients<PyreflyLspIntegrationProvider>().firstOrNull() ?: return null

    return PyreflyLspTypeEngine(module, server)
  }

  object Util {
    fun isAvailable(project: Project): Boolean {
      return (PyTypeEngineUtils.isExternalTypeEngineSupported(project) ||
              Registry.`is`("pyrefly.type.engine") ||
              ApplicationManager.getApplication().isUnitTestMode)
    }
  }
}