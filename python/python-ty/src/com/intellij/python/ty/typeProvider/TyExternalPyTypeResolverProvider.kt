package com.intellij.python.ty.typeProvider

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineUtils
import com.intellij.python.ty.TyLspIntegrationProvider
import com.jetbrains.python.psi.types.engine.PyTypeEngine
import com.jetbrains.python.psi.types.engine.PyTypeEngineProvider

/**
 * External type provider that delegates to ty's LSP endpoint.
 * This provider is enabled when the Type Engine feature is enabled via registry.
 * The actual per-module check for ty configuration is done in TyTypeResolver.isSupportedForResolve.
 */
class TyExternalPyTypeResolverProvider : PyTypeEngineProvider {
  override fun createResolver(module: Module): PyTypeEngine? {
    // Check if type engine feature is enabled (via registry or unit tests)
    val isFeatureEnabled = isTyTypeProviderFeatureEnabled(module.project)
    if (!isFeatureEnabled) {
      return null
    }

    // Check if LSP server exists - if not, we can't provide types
    val lspServerExists = LspClientManager.getInstance(module.project).getClients(TyLspIntegrationProvider::class.java).isNotEmpty()
    if (!lspServerExists) {
      return null
    }
    return TyTypeResolver()
  }

}

internal fun isTyTypeProviderFeatureEnabled(project: Project): Boolean =
  ApplicationManager.getApplication().isUnitTestMode ||
  PyTypeEngineUtils.isExternalTypeEngineSupported(project) &&
  Registry.`is`("ty.type.engine.support")