// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.openapi.application.ApplicationManager
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

  override fun toString(): String = name

  companion object {
    @JvmStatic
    val isEnabled: Boolean get() = Registry.`is`("python.type.any")

    fun validate(it: PyType?) {
      if (!ApplicationManager.getApplication().isInternal) return

      if (isEnabled && it == null)
        throw AssertionError("a type with a value of `null` was encountered while `PyAnyType` was enabled")
      if (!isEnabled && it is PyAnyType)
        throw AssertionError("a type with a value of `PyAnyType` was encountered while `PyAnyType` was disabled")
    }

    @JvmStatic
    val any: Any? get() = if (isEnabled) Any else null
    @JvmStatic
    val unknown: Unknown? get() = if (isEnabled) Unknown else null
  }
}
