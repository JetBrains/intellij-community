// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyPsiFacade
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.RatedResolveResult
import org.jetbrains.annotations.ApiStatus

/**
 * The top type of the Python type hierarchy: the supertype of every type, equivalent to `builtins.object`.
 *
 * There is only ever one top type, so this is a singleton. Unlike a [PyClassType] wrapping `object`, it needs no
 * anchor [PsiElement] to be produced.
 */
@ApiStatus.Experimental
object PyTopType : PyType {
  override val name: String = "object"

  override val isBuiltin: Boolean = true

  override fun assertValid(message: String?) {}

  override fun resolveMember(
    name: String,
    location: PyExpression?,
    direction: AccessDirection,
    resolveContext: PyResolveContext,
  ): List<RatedResolveResult> =
    objectType(location)?.resolveMember(name, location, direction, resolveContext).orEmpty()

  override fun getCompletionVariants(
    completionPrefix: String?,
    location: PsiElement,
    context: ProcessingContext,
  ): Array<out Any> =
    objectType(location)?.getCompletionVariants(completionPrefix, location, context).orEmpty()

  override fun getAllMembers(resolveContext: PyResolveContext): List<PyTypeMember> =
    objectType(resolveContext.typeEvalContext.origin)?.getAllMembers(resolveContext).orEmpty()

  override fun findMember(name: String, resolveContext: PyResolveContext): List<PyTypeMember> =
    objectType(resolveContext.typeEvalContext.origin)?.findMember(name, resolveContext).orEmpty()

  private fun objectType(anchor: PsiElement?): PyClassType? {
    if (anchor == null) return null
    val facade = PyPsiFacade.getInstance(anchor.project)
    val objectClass = facade.createClassByQName(PyNames.OBJECT, anchor) ?: return null
    return facade.createClassType(objectClass, false)
  }

  override fun <T> acceptTypeVisitor(visitor: PyTypeVisitor<T>): T? = visitor.visitPyTopType(this)

  override fun toString(): String = name
}
