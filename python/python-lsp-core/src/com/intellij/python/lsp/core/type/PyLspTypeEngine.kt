package com.intellij.python.lsp.core.type

import com.intellij.lang.injection.InjectedLanguageManager
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

    // An injected fragment (`.. code-block:: python` in a docstring, Python inside a string literal, ...)
    // lives in a VirtualFileWindow over a DocumentWindow, which the LSP server never saw and which the
    // LSP coordinate API rejects outright. Notebooks are unaffected: Jupyter exposes its Python cells
    // through a template-language view provider over the real .ipynb document, not through injection.
    if (InjectedLanguageManager.getInstance(realFile.project).isInjectedFragment(realFile))
      return false

    val isSupportedTypesVisitor = LspIsSupportedTypesVisitor()
    pyTypedElement.accept(isSupportedTypesVisitor)
    return isSupportedTypesVisitor.isSupported
  }
}