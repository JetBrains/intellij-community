/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.codeInsight.stdlib

import com.intellij.openapi.util.Ref
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.*

class PyDataclassesTypeProvider : PyTypeProviderBase() {

  override fun getReferenceExpressionType(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
    return getDataclassTypeForCallee(referenceExpression, context)
  }

  override fun getParameterType(param: PyNamedParameter, func: PyFunction, context: TypeEvalContext): Ref<PyType>? {
    if (!param.isPositionalContainer && !param.isKeywordContainer && param.annotationValue == null && func.name == DUNDER_POST_INIT) {
      val cls = func.containingClass

      if (cls != null && parseDataclassParameters(cls, context)?.init == true) {
        var result: Ref<PyType>? = null

        cls.processClassLevelDeclarations { element, _ ->
          if (element is PyTargetExpression && element.name == param.name && element.annotationValue != null) {
            result = Ref.create(getTypeForParameter(element, context))
            false
          }
          else {
            true
          }
        }

        return result
      }
    }

    return null
  }

  private fun getDataclassTypeForCallee(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyCallableType? {
    if (PyCallExpressionNavigator.getPyCallExpressionByCallee(referenceExpression) == null) return null

    val resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context)
    val resolveResults = referenceExpression.getReference(resolveContext).multiResolve(false)

    return PyUtil.filterTopPriorityResults(resolveResults)
      .asSequence()
      .filterIsInstance<PyClass>()
      .map { getDataclassTypeForClass(it, context) }
      .firstOrNull { it != null }
  }

  private fun getDataclassTypeForClass(cls: PyClass, context: TypeEvalContext): PyCallableType? {
    val dataclassParameters = parseDataclassParameters(cls, context)
    if (dataclassParameters == null || !dataclassParameters.init) {
      return null
    }

    val parameters = ArrayList<PyCallableParameter>()

    cls.processClassLevelDeclarations { element, _ ->
      if (element is PyTargetExpression && element.annotationValue != null) {
        val annotation = element.annotation

        if (annotation != null && !PyTypingTypeProvider.isClassVarAnnotation(annotation, context)) {
          parameters.add(PyCallableParameterImpl.nonPsi(element.name, getTypeForParameter(element, context), element.findAssignedValue()))
        }
      }

      true
    }

    return PyCallableTypeImpl(parameters, context.getType(cls))
  }

  private fun getTypeForParameter(element: PyTargetExpression, context: TypeEvalContext): PyType? {
    val type = context.getType(element)
    if (type is PyCollectionType && type is PyClassType && type.classQName == DATACLASSES_INITVAR_TYPE) {
      return type.elementTypes.firstOrNull()
    }
    return type
  }
}