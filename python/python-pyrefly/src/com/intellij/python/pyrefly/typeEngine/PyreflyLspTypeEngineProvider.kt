package com.intellij.python.pyrefly.typeEngine

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.ensureClientStarted
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineUtils
import com.intellij.python.pyrefly.PyreflyExecutableProvider
import com.intellij.python.pyrefly.PyreflyPyTool
import com.intellij.python.pyrefly.lsp.PyreflyLspIntegrationProvider
import com.intellij.python.pyrefly.lsp.pyreflyDescriptor
import com.jetbrains.python.psi.types.engine.PyTypeEngine
import com.jetbrains.python.psi.types.engine.PyTypeEngineProvider


/**
 * The type engine provider that delegates to the Pyrefly server.
 *
 * The answer depends on the settings and the project model alone, not on the state of the server. So
 * every context of a module gets the same engine, whether the server runs or still starts. The
 * engine answers `Unknown` until the server runs, see [PyreflyLspTypeEngine].
 */
class PyreflyLspTypeEngineProvider : PyTypeEngineProvider {
  override fun createTypeEngine(module: Module): PyTypeEngine? {
    val project = module.project
    // The registry key or the unit-test mode can enable the feature.
    if (!Util.isAvailable(project)) {
      return null
    }

    // Provide types only when Pyrefly is the selected type engine, not when it is enabled as an LSP
    // tool alone. This decouples type inference from the External Tools toggle (PY-90550).
    if (!PyreflyPyTool.getInstance().isSelectedAsTypeEngine(project)) {
      return null
    }

    // Skip a module whose interpreter Pyrefly cannot drive: PyreflyLspClientDescriptor
    // .startServerProcess would throw. `isAvailable` above does not imply this, because the
    // `pyrefly.type.engine` key and unit-test mode both bypass the interpreter check.
    if (!PyTypeEngineUtils.isLocalNonReadOnlySdk(module)) {
      return null
    }

    // An untrusted project starts no server, see `LspClientManagerImpl.ensureStarted`, so the engine
    // would never be ready. The built-in engine answers there.
    if (!TrustedProjects.isProjectTrusted(project)) {
      return null
    }

    if (!PyreflyExecutableProvider.executableExists()) {
      return null
    }

    LspClientManager.getInstance(project)
      .ensureClientStarted<PyreflyLspIntegrationProvider>(pyreflyDescriptor(module))
    return PyreflyLspTypeEngine(module)
  }

  object Util {
    fun isAvailable(project: Project): Boolean {
      return (PyTypeEngineUtils.isExternalTypeEngineSupported(project) ||
              Registry.`is`("pyrefly.type.engine") ||
              ApplicationManager.getApplication().isUnitTestMode)
    }
  }
}
