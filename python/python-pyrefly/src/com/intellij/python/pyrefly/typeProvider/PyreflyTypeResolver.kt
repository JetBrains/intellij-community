package com.intellij.python.pyrefly.typeProvider

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.lsp.api.LspClient
import com.intellij.psi.util.parents
import com.intellij.python.lsp.core.type.LspPyTypeResolver
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineType
import com.intellij.python.pyrefly.PyreflyConfiguration
import com.intellij.python.pyrefly.PyreflyPyTool
import com.intellij.python.pytools.isEnabledOn
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.PyType

internal class PyreflyTypeResolver(private val module: Module, val lspClient: LspClient) : LspPyTypeResolver {
  override val name: String = PyTypeEngineType.PYREFLY.name

  override fun isSupportedForResolve(pyTypedElement: PyTypedElement): Boolean {
    // First check the base class conditions
    if (!super.isSupportedForResolve(pyTypedElement)) {
      return false
    }
    // when there is `from x.y`, for `y` pyrefly will return `Module[x]`, so we do it ourselves
    if (
      pyTypedElement.parents(false).any { it is PyFromImportStatement }
      && pyTypedElement is PyQualifiedExpression
      && pyTypedElement.isQualified
    ) {
      return false
    }
    return Registry.`is`("pyrefly.type.engine") || PyreflyPyTool.getInstance().isEnabledOn(module.project)
  }

  override fun resolveType(pyTypedElement: PyTypedElement, isLibrary: Boolean, isUserInitiated: Boolean): Ref<PyType?>? {
    val realFile = pyTypedElement.containingFile?.originalFile ?: return null

    val pyreflyTypeEvalContext = PyreflyTypeResolverFileCache.getInstance(module.project).getContext(realFile, lspClient, isLibrary)
    return pyreflyTypeEvalContext.provideType(pyTypedElement, isUserInitiated)
  }
}