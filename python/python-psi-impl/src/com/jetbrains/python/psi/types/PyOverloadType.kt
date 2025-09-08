package com.jetbrains.python.psi.types

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.RatedResolveResult
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class PyOverloadType(val items: List<PyCallableType?>, val impl: Ref<PyType?>?) : PyType {

  override fun resolveMember(
    name: String,
    location: PyExpression?,
    direction: AccessDirection,
    resolveContext: PyResolveContext,
  ): List<RatedResolveResult> = emptyList()

  override fun getCompletionVariants(completionPrefix: String?, location: PsiElement, context: ProcessingContext): Array<out Any> =
    emptyArray()

  override val name: String = PyNames.OVERLOAD_TYPE

  override val isBuiltin: Boolean = false

  override fun assertValid(message: String?) {
  }

  override fun <T> acceptTypeVisitor(visitor: PyTypeVisitor<T>): T? {
    if (visitor is PyTypeVisitorExt) {
      return visitor.visitPyOverloadType(this);
    }
    return visitor.visitPyType(this)
  }
}
