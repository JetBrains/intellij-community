package com.intellij.python.lsp.core.type

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.platform.lsp.api.LspClient
import com.intellij.python.lsp.core.isUsable
import com.jetbrains.python.psi.PyExpressionCodeFragment
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.engine.PyTypeEngine
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PyLspTypeEngine : PyTypeEngine {
  /**
   * The client whose server answers for this engine, or `null` while no running server does. One server
   * can answer for several modules.
   */
  val lspClient: LspClient?

  /**
   * A stopped or starting server answers nothing, see [isUsable]. The context then answers `Unknown`
   * and does not cache the gap.
   */
  override val isReady: Boolean
    get() = lspClient?.isUsable == true

  override fun isSupportedForResolve(pyTypedElement: PyTypedElement): Boolean {
    // A light element such as `PyImportedModule` belongs to a file but has no text in it, so it has no
    // position to send to the server.
    if (pyTypedElement.textRange == null)
      return false

    val realFile = pyTypedElement.containingFile?.originalFile ?: return false
    if (realFile is PyExpressionCodeFragment)
      return false

    // An injected fragment (`.. code-block:: python` in a docstring, Python inside a string literal, ...)
    // lives in a VirtualFileWindow over a DocumentWindow, which the LSP server never saw and which the
    // LSP coordinate API rejects outright. Notebooks are unaffected: Jupyter exposes its Python cells
    // through a template-language view provider over the real .ipynb document, not through injection.
    return !InjectedLanguageManager.getInstance(realFile.project).isInjectedFragment(realFile)
  }
}
