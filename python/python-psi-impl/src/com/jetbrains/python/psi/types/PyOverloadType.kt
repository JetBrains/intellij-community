// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  private val cachedHashCode: Int by lazy(LazyThreadSafetyMode.PUBLICATION) { 31 * items.hashCode() + impl?.get().hashCode() }

  override fun hashCode(): Int = cachedHashCode

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PyOverloadType) return false
    return cachedHashCode == other.cachedHashCode && items == other.items && impl?.get() == other.impl?.get()
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
