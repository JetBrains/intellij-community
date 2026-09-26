// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.RatedResolveResult
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a type of an expression.
 */
interface PyType {
  /**
   * Returns the declaration element that can be used to refer to this type inside type hints. Normally, it's a symbol
   * that can be imported to mentioned the type in type annotations and comments anywhere else.
   *
   *
   * Typical examples are target expressions in LHS of assignments in `TypeVar` and named tuple definitions, as well as
   * class definitions themselves for plain class and generic types.
   */
  val declarationElement: PyQualifiedNameOwner? get() = null

  /**
   * Resolves an attribute of type.
   *
   * @param name     attribute name
   * @param location the expression of type qualifierType on which the member is being resolved (optional)
   * @return null if name definitely cannot be found (e.g. in a qualified reference),
   * or an empty list if name is not found but other contexts are worth looking at,
   * or a list of elements that define the name, a la multiResolve().
   */
  fun resolveMember(
    name: String,
    location: PyExpression?,
    direction: AccessDirection,
    resolveContext: PyResolveContext,
  ): List<@JvmWildcard RatedResolveResult>?


  @ApiStatus.Experimental
  fun getAllMembers(resolveContext: PyResolveContext): List<PyTypeMember> {
    return emptyList()
  }

  /**
   * Returns a list of members with a given name
   * There can be several members with the same name (for example, methods with @overload)
   */
  @ApiStatus.Experimental
  fun findMember(name: String, resolveContext: PyResolveContext): List<PyTypeMember> {
    return emptyList()
  }

  /**
   * Proposes completion variants from type's attributes.
   *
   * @param location the reference on which the completion was invoked
   * @param context  to share state between nested invocations
   * @return completion variants good for [com.intellij.psi.PsiReference.getVariants] return value.
   */
  fun getCompletionVariants(completionPrefix: String?, location: PsiElement, context: ProcessingContext): Array<out Any>

  @get:NlsSafe
  val name: @NlsSafe String?

  /**
   * @return true if the type is a known built-in type.
   */
  val isBuiltin: Boolean

  fun assertValid(message: String?)

  /**
   * For nullable `PyType` instance use [PyTypeVisitor.visit]
   * to visit `null` values with [PyTypeVisitor.visitUnknownType].
   */
  @ApiStatus.Experimental
  fun <T> acceptTypeVisitor(visitor: PyTypeVisitor<T>): T? {
    return visitor.visitPyType(this)
  }

  companion object {
    /**
     * Context key for access to a set of names already found by variant search.
     */
    @JvmField
    val CTX_NAMES: Key<MutableSet<String>> = Key("Completion variants names")
  }
}
