// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.openapi.util.registry.Registry
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
sealed class PyAnyType private constructor(override val name: String) : PyType {

  object Any : PyAnyType(PyNames.ANY_TYPE)
  object Unknown : PyAnyType(PyNames.UNKNOWN_TYPE)

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

  override fun <T> acceptTypeVisitor(visitor: PyTypeVisitor<T>): T? = when(this) {
    is Any -> visitor.visitAnyType()
    is Unknown -> visitor.visitUnknownType()
  }

  companion object {
    @JvmStatic
    val isEnabled: Boolean get() = Registry.`is`("python.type.any")

    val any: Any? get() = if (isEnabled) Any else null
    val unknown: Unknown? get() = if (isEnabled) Unknown else null
  }
}
