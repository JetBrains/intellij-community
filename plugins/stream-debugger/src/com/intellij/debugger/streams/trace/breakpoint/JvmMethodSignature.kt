// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.util.TypeConversionUtil
import com.sun.jdi.Method

/**
 * Language agnostic representation of a method.
 * The method signature description reflects Java runtime semantics,
 * so function on any JVM language can be represented.
 */
data class JvmMethodSignature(
  val classFqn: String,
  val name: String,
  val argumentTypes: List<String>,
  val returnType: String
) {
  private val arguments: String
      get() = argumentTypes.joinToString(", ")

  override fun toString(): String = "$returnType $classFqn.$name($arguments)"

  companion object {
    fun of(method: Method): JvmMethodSignature = JvmMethodSignature(
      method.declaringType().name(),
      method.name(),
      method.argumentTypeNames(),
      method.returnTypeName()
    )

    fun of(psiMethod: PsiMethod): JvmMethodSignature = JvmMethodSignature(
      psiMethod.containingClass?.qualifiedName ?: "",
      psiMethod.name,
      psiMethod.parameterList.parameters.map { it.signature() },
      TypeConversionUtil.erasure(psiMethod.returnType)?.canonicalText ?: ""
    )

    private fun PsiParameter.signature(): String {
      val paramType = TypeConversionUtil.erasure(this.type)
      if (this.isVarArgs) {
        return "${paramType.deepComponentType.canonicalText}[]"
      }

      return paramType.canonicalText
    }
  }
}