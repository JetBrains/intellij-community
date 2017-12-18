/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.codeInsight.stdlib

import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.*

class PyDataclassesTypeProvider : PyTypeProviderBase() {

  override fun getReferenceExpressionType(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
    return getDataclassTypeForCallee(referenceExpression, context)
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
    if (type is PyCollectionType && type is PyClassType && type.classQName == "dataclasses.InitVar") {
      return type.elementTypes.firstOrNull()
    }
    return type
  }
}