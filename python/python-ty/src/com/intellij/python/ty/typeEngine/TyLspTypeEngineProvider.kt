package com.intellij.python.ty.typeEngine

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.python.lsp.core.findLspClientFor
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineUtils
import com.intellij.python.pyproject.model.evolution.findEvoPyProjectIfReady
import com.intellij.python.ty.TyLspIntegrationProvider
import com.jetbrains.python.psi.types.engine.PyTypeEngine
import com.jetbrains.python.psi.types.engine.PyTypeEngineProvider
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * External type provider that delegates to ty's LSP endpoint.
 * This provider is enabled when the Type Engine feature is enabled via registry.
 * The actual per-module check for ty configuration is done in [TyLspTypeEngine.isSupportedForResolve].
 */
class TyLspTypeEngineProvider : PyTypeEngineProvider {
  override fun createTypeEngine(module: Module): PyTypeEngine? {
    // Check if type engine feature is enabled (via registry or unit tests)
    val isFeatureEnabled = isTyTypeEngineFeatureEnabled(module.project)
    if (!isFeatureEnabled) {
      return null
    }

    // Take the client that answers for *this* module. Another module's server answers for another
    // module's content roots and resolves everything to `Any`. This runs under a read lock, so it
    // reads the snapshot and does not wait. A module with no Python project has no ty server.
    val pyProject = module.findEvoPyProjectIfReady()?.pyProject ?: return null
    val lspClient = findLspClientFor(pyProject, TyLspIntegrationProvider::class.java) ?: return null
    return TyLspTypeEngine(module, lspClient)
  }

}

@Internal
fun isTyTypeEngineFeatureEnabled(project: Project): Boolean =
  ApplicationManager.getApplication().isUnitTestMode ||
  PyTypeEngineUtils.isExternalTypeEngineSupported(project) &&
  Registry.`is`("ty.type.engine.support")
