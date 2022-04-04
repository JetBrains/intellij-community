// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.util.TypeConversionUtil
import com.sun.jdi.Method

data class MethodSignature(val containingClass: String, val name: String, val argumentTypes: List<String>, val returnType: String) {
  val arguments: String
    get() = argumentTypes.joinToString(", ")

  override fun toString() = "$returnType $containingClass.$name($arguments)"

  companion object {
    fun of(method: Method) = MethodSignature(
      method.declaringType().name(),
      method.name(),
      method.argumentTypeNames(),
      method.returnTypeName()
    )

    fun of(psiMethod: PsiMethod) = MethodSignature(
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