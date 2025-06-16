package com.jetbrains.python.validation

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.python.PythonRuntimeService
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.psi.PyExpressionCodeFragment
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PyImplicitAsyncContextProvider {
  fun isImplicitAsyncContext(scopeOwner: ScopeOwner): Boolean

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PyImplicitAsyncContextProvider> = ExtensionPointName.create<PyImplicitAsyncContextProvider>("Pythonid.implicitAsyncContext")
  }
}

class PyDevConsoleAsyncContextProvider : PyImplicitAsyncContextProvider {
  override fun isImplicitAsyncContext(scopeOwner: ScopeOwner): Boolean {
    // Top-level expressions in the Python console are allowed to contain "await", "async with" and "async for"
    return scopeOwner is PyExpressionCodeFragment && PythonRuntimeService.getInstance().isInPydevConsole(scopeOwner)
  }
}

