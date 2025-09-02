// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.RatedResolveResult

class PyNeverType private constructor(private val name: String): PyType {
  companion object {
    @JvmField val NEVER: PyNeverType = PyNeverType("Never")
    @JvmField val NO_RETURN: PyNeverType = PyNeverType("NoReturn")

    @JvmStatic
    fun PyType?.toNoReturnIfNeeded(): PyType? = if (this === NEVER) NO_RETURN else this
  }
  
  override fun getName(): String = name
  override fun isBuiltin(): Boolean = true
  override fun assertValid(message: String?) {}

  override fun equals(other: Any?): Boolean = other is PyNeverType
  override fun hashCode(): Int = name.hashCode()

  override fun resolveMember(
    name: String,
    location: PyExpression?,
    direction: AccessDirection,
    resolveContext: PyResolveContext,
  ): List<RatedResolveResult> = emptyList()
  
  override fun getCompletionVariants(
    completionPrefix: String?,
    location: PsiElement?,
    context: ProcessingContext?,
  ): Array<Any> = emptyArray()

  override fun <T> acceptTypeVisitor(visitor: PyTypeVisitor<T>): T {
    return visitor.visitPyNeverType(this)
  }
}