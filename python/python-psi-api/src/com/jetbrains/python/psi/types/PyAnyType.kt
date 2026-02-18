// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.RatedResolveResult
import org.jetbrains.annotations.ApiStatus

/**
 * represents `typing.Any` and `Unknown`/untyped
 *
 * currently unused
 */
@ApiStatus.Experimental
class PyAnyType private constructor(override val name: String) : PyType {
  override fun resolveMember(
    name: String,
    location: PyExpression?,
    direction: AccessDirection,
    resolveContext: PyResolveContext,
  ): List<RatedResolveResult> {
    return emptyList()
  }

  override fun getCompletionVariants(completionPrefix: String?, location: PsiElement, context: ProcessingContext): Array<Any> {
    return emptyArray()
  }

  override val isBuiltin: Boolean = false

  override fun assertValid(message: String?) {
  }

  override fun <T> acceptTypeVisitor(visitor: PyTypeVisitor<T>): T? =
    if (this === Any) visitor.visitAnyType()
    else visitor.visitUnknownType()

  companion object {
    val Any: PyAnyType = PyAnyType(PyNames.ANY_TYPE)
    val Unknown: PyAnyType = PyAnyType(PyNames.UNKNOWN_TYPE)
  }
}
