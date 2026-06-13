package com.jetbrains.python.validation

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.python.PythonRuntimeService
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.psi.PyExpressionCodeFragment
import com.jetbrains.python.psi.PyFunction
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PyImplicitAsyncContextProvider {
  fun isImplicitAsyncContext(scopeOwner: ScopeOwner): Boolean

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PyImplicitAsyncContextProvider> = ExtensionPointName.create<PyImplicitAsyncContextProvider>("Pythonid.implicitAsyncContext")

    /**
     * Whether `await`, `async for` and `async with` are allowed in [scopeOwner], i.e. it is an async function or an
     * implicit async context (such as the Python console or a Jupyter notebook).
     */
    @JvmStatic
    fun isAsyncAllowed(scopeOwner: ScopeOwner?): Boolean {
      if (scopeOwner == null) return false
      if (scopeOwner is PyFunction && scopeOwner.isAsync) return true
      return EP_NAME.extensionList.any { it.isImplicitAsyncContext(scopeOwner) }
    }
  }
}

class PyDevConsoleAsyncContextProvider : PyImplicitAsyncContextProvider {
  override fun isImplicitAsyncContext(scopeOwner: ScopeOwner): Boolean {
    // Top-level expressions in the Python console are allowed to contain "await", "async with" and "async for"
    return scopeOwner is PyExpressionCodeFragment && PythonRuntimeService.getInstance().isInPydevConsole(scopeOwner)
  }
}
