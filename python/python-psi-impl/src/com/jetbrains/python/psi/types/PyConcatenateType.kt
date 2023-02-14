package com.jetbrains.python.psi.types

import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.RatedResolveResult

/**
 * Type of typing.Concatenate to store corresponding first type and parameter specification
 */
class PyConcatenateType(val firstTypes: List<PyType?>, val paramSpec: PyParamSpecType): PyType {

  override fun resolveMember(name: String,
                             location: PyExpression?,
                             direction: AccessDirection,
                             resolveContext: PyResolveContext): List<RatedResolveResult>? {
    return null
  }

  override fun getCompletionVariants(completionPrefix: String?, location: PsiElement?, context: ProcessingContext?): Array<Any> =
    emptyArray()

  override fun getName(): String = "Concatenate(${firstTypes.joinToString { it?.name ?: "Any" }}, ${paramSpec.name})"

  override fun isBuiltin(): Boolean = true

  override fun assertValid(message: String?) {
  }
}