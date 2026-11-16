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
class PyOverloadType(val items: List<PyCallableType>, val impl: Ref<PyType?>?) : PyType {

  /**
   * The type of the object that the overloaded name refers to at runtime:
   * the implementation or, when its type is not known, the first overload.
   */
  internal val runtimeType: PyType?
    get() = impl?.get() ?: items.firstOrNull()

  override fun resolveMember(
    name: String,
    location: PyExpression?,
    direction: AccessDirection,
    resolveContext: PyResolveContext,
  ): List<RatedResolveResult>? {
    val runtimeType = runtimeType ?: return emptyList()
    return runtimeType.resolveMember(name, location, direction, resolveContext)
  }

  override fun getCompletionVariants(completionPrefix: String?, location: PsiElement, context: ProcessingContext): Array<out Any> =
    runtimeType?.getCompletionVariants(completionPrefix, location, context) ?: emptyArray()

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

@ApiStatus.Experimental
fun PyOverloadType.map(mapper: (PyCallableType) -> PyCallableType?): PyType? {
  val newItems = items.mapNotNull(mapper)
  if (newItems.isEmpty()) return null
  if (newItems.size == 1) return newItems.first()
  var newImpl = impl
  if (impl != null) {
    val impl_ = impl.get()
    if (impl_ is PyCallableType) {
      newImpl = Ref.create(mapper(impl_))
    }
  }
  return PyOverloadType(newItems, newImpl)
}
