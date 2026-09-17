package com.intellij.python.pyrefly.typeEngine

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Ref
import com.intellij.platform.lsp.api.LspClient
import com.intellij.python.lsp.core.findLspClientForModule
import com.intellij.python.lsp.core.type.PyLspTypeEngine
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineType
import com.intellij.python.pyrefly.lsp.PyreflyLspIntegrationProvider
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.PyType

/**
 * The engine exists as soon as Pyrefly is the selected type engine, before its server runs. It holds
 * no client. It looks the client up on each call, so it survives a restart of the server, and it
 * answers nothing until the server runs, see [isReady].
 */
internal class PyreflyLspTypeEngine(override val module: Module) : PyLspTypeEngine {
  override val name: String = PyTypeEngineType.PYREFLY.name
  override val allowsBuiltInTypeEngineFallbackWhenUnavailable: Boolean = false

  override val lspClient: LspClient?
    get() = findLspClientForModule(module, PyreflyLspIntegrationProvider::class.java)

  /**
   * Pyrefly answers for every element its server sees. The visitor of [PyLspTypeEngine] lists what a
   * plain LSP server answers, and Pyrefly answers more through its own requests.
   */
  override fun isSupportedForResolve(pyTypedElement: PyTypedElement): Boolean = isVisibleToServer(pyTypedElement)

  override fun resolveType(pyTypedElement: PyTypedElement, isLibrary: Boolean, isUserInitiated: Boolean): Ref<PyType?>? {
    val realFile = pyTypedElement.containingFile?.originalFile ?: return null
    val client = lspClient ?: return null

    val context = PyreflyLspTypeEngineFileCache.getInstance(module.project).getContext(realFile, client, isLibrary)
    return context.provideType(pyTypedElement, isUserInitiated)
  }
}
