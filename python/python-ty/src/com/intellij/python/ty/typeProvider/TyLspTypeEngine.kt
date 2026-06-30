package com.intellij.python.ty.typeProvider

import com.intellij.openapi.util.Ref
import com.intellij.python.lsp.core.type.PyLspTypeEngine
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProjectSettings
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineType
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.PyType

internal class TyLspTypeEngine : PyLspTypeEngine {
  override val name: String = PyTypeEngineType.TY.name

  override fun isSupportedForResolve(pyTypedElement: PyTypedElement): Boolean {
    // First check the base class conditions
    if (!super.isSupportedForResolve(pyTypedElement)) {
      return false
    }

    // Check if the module's type engine is set to TY
    return PyTypeEngineProjectSettings.getInstance(pyTypedElement.project).typeEngine == PyTypeEngineType.TY
  }

  override fun resolveType(pyTypedElement: PyTypedElement, isLibrary: Boolean, isUserInitiated: Boolean): Ref<PyType?>? {
    val context = TyTypeContext(pyTypedElement.containingFile)
    return context.provideType(pyTypedElement, isUserInitiated)
  }
}
