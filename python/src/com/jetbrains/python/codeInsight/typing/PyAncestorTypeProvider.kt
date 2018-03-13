// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.util.Ref
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.getReturnTypeAnnotation
import com.jetbrains.python.psi.PyCallable
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.search.PySuperMethodsSearch
import com.jetbrains.python.psi.types.PyFunctionType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.TypeEvalContext

class PyAncestorTypeProvider : PyTypeProviderBase() {

  override fun getParameterType(param: PyNamedParameter, func: PyFunction, context: TypeEvalContext): Ref<PyType>? {
    val superFunctionType = getOverriddenFunctionType(func, context)

    if (superFunctionType != null) {
      val superFunctionTypeRemovedSelf = superFunctionType.dropSelf(context)
      val parameters = superFunctionTypeRemovedSelf.getParameters(context)
      parameters?.forEach {
        val parameterName = it.name
        if (parameterName != null && parameterName == param.name) {
          it.getType(context)?.let { return Ref(it) }
        }
      }
    }
    return null
  }

  private fun getOverriddenFunctionType(function: PyFunction, context: TypeEvalContext): PyFunctionType? {
    val overriddenFunction = getOverriddenFunction(function, context)
    if (overriddenFunction != null) {
      val type = context.getType(overriddenFunction)
      if (type is PyFunctionType) {
        return context.getType(overriddenFunction) as PyFunctionType?
      }
    }
    return null
  }

  private fun getOverriddenFunction(function: PyFunction, context: TypeEvalContext): PyFunction? {
    val superMethodSearchQuery = PySuperMethodsSearch.search(function, context)
    val firstSuperMethod = superMethodSearchQuery.findFirst() as? PyFunction ?: return null

    if (firstSuperMethod.decoratorList != null) {
      if (firstSuperMethod.decoratorList!!.decorators
        .mapNotNull { it.name }
        .any { PyNames.OVERLOAD == it }) {
        return null
      }
    }

    val superClass = firstSuperMethod.containingClass
    return if (superClass != null && PyNames.OBJECT != superClass.name) {
      firstSuperMethod
    }
    else null
  }

  override fun getReturnType(callable: PyCallable, context: TypeEvalContext): Ref<PyType>? {
    if (callable is PyFunction) {
      val typeFromSupertype = getReturnTypeFromSupertype(callable, context)
      if (typeFromSupertype != null) {
        return Ref.create(PyTypingTypeProvider.toAsyncIfNeeded(callable, typeFromSupertype.get()))
      }
    }
    return null
  }

  /**
   * Get function return type from supertype.
   *
   * The only source of type information in current implementation is annotation. This is to avoid false positives,
   * that may arise from non direct type estimations (not from annotation, nor form type comments).
   *
   * TODO: switch to return type direct usage when type information source will be available.
   *
   * @param function
   * @param context
   * @return
   */
  private fun getReturnTypeFromSupertype(function: PyFunction, context: TypeEvalContext): Ref<PyType>? {
    val overriddenFunction = getOverriddenFunction(function, context)

    if (overriddenFunction != null) {
      val superFunctionAnnotation = getReturnTypeAnnotation(overriddenFunction, context)
      if (superFunctionAnnotation != null) {
        val typeRef = PyTypingTypeProvider.getType(superFunctionAnnotation, PyTypingTypeProvider.Context(context))
        if (typeRef != null) {
          return Ref.create(PyTypingTypeProvider.toAsyncIfNeeded(function, typeRef.get()))
        }
      }
    }
    return null
  }
}
