package com.intellij.python.lsp.core.type

import com.jetbrains.python.psi.PyExpressionCodeFragment
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.engine.PyTypeEngine
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PyLspTypeEngine : PyTypeEngine {
  override fun isSupportedForResolve(pyTypedElement: PyTypedElement): Boolean {
    val realFile = pyTypedElement.containingFile?.originalFile ?: return false
    if (realFile is PyExpressionCodeFragment)
      return false

    val isSupportedTypesVisitor = LspIsSupportedTypesVisitor()
    pyTypedElement.accept(isSupportedTypesVisitor)
    return isSupportedTypesVisitor.isSupported
  }
}