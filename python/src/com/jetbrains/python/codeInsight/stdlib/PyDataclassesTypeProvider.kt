/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.codeInsight.stdlib

import com.jetbrains.python.psi.*
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
    if (!PyKnownDecoratorUtil.getKnownDecorators(cls, context).contains(PyKnownDecoratorUtil.KnownDecorator.DATACLASSES_DATACLASS)) {
      return null
    }

    val parameters = ArrayList<PyCallableParameter>()

    cls.processClassLevelDeclarations { element, _ ->
      if (element is PyTargetExpression && element.annotationValue != null) {
        parameters.add(PyCallableParameterImpl.nonPsi(element.name, context.getType(element), element.findAssignedValue()))
      }
      true
    }

    return PyCallableTypeImpl(parameters, context.getType(cls))
  }
}