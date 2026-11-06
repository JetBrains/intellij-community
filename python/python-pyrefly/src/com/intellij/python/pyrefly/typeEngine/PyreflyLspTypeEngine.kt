package com.intellij.python.pyrefly.typeEngine

import com.intellij.openapi.util.Ref
import com.intellij.platform.lsp.api.LspClient
import com.intellij.psi.util.parents
import com.intellij.python.lsp.core.findLspClientFor
import com.intellij.python.lsp.core.type.PyLspTypeEngine
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineType
import com.intellij.python.pyrefly.lsp.PyreflyLspIntegrationProvider
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyKeyValueExpression
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.PyType

/**
 * The engine exists as soon as Pyrefly is the selected type engine, before its server runs. It holds
 * no client. It looks the client up on each call, so it survives a restart of the server, and it
 * answers nothing until the server runs, see [isReady].
 */
internal class PyreflyLspTypeEngine(private val pyProject: PyProject) : PyLspTypeEngine {
  override val name: String = PyTypeEngineType.PYREFLY.name

  override val lspClient: LspClient?
    get() = findLspClientFor(pyProject, PyreflyLspIntegrationProvider::class.java)

  /**
   * Pyrefly answers for every element its server sees.
   */
  override fun isSupportedForResolve(pyTypedElement: PyTypedElement): Boolean {
    if (!super.isSupportedForResolve(pyTypedElement)) return false
    // when there is `from x.y`, for `y` pyrefly will return `Module[x]`, so we do it ourselves
    if (
      pyTypedElement.parents(false).any { it is PyFromImportStatement }
      && pyTypedElement is PyQualifiedExpression
      && pyTypedElement.isQualified
    ) {
      return false
    }
    if (pyTypedElement is PyKeyValueExpression) return false
    return true
  }

  override fun resolveType(pyTypedElement: PyTypedElement, isUserInitiated: Boolean): Ref<PyType?>? {
    val client = lspClient ?: return null
    val context = PyreflyTypeEvalContextCache.getInstance(pyProject.residesOnModule.project).getContext(client)
    return context.provideType(pyTypedElement)
  }
}
