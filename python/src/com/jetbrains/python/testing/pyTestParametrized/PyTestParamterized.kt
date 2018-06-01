// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pyTestParametrized

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.types.*
import com.jetbrains.python.testing.isTestElement

/**
 * @return Boolean is parameter provided to function by parametrized decorator
 */
internal fun PyNamedParameter.isParametrized(evalContext: TypeEvalContext) = asParametrized(evalContext) != null


/**
 * Fetch [PyTestParameter] associated with certain param
 */
internal fun PyNamedParameter.asParametrized(evalContext: TypeEvalContext) = PsiTreeUtil.getParentOfType(this, PyFunction::class.java)
  ?.getParametersOfParametrized(evalContext)
  ?.find { it.name == name }


private fun getParametersFromDecorator(decorator: PyDecorator, evalContext: TypeEvalContext): List<PyTestParameter> {
  val decoratorArguments = decorator.arguments
  val evaluator = PyEvaluator()
  val parameterNamesExpression = evaluator.evaluate(decoratorArguments.firstOrNull()) ?: return emptyList()
  // (parameterNamesExpression, [valuesExpression])
  val valuesExpression = (decoratorArguments.getOrNull(1) as? PyTypedElement)?.let { evalContext.getType(it) } as? PyCollectionType


  val parameterNames = when (parameterNamesExpression) {
  //For cases when parameters are written as literals "spam,eggs"
    is String -> parameterNamesExpression.split(',').map(String::trim)
  // For cases when written as tuple or list: ("spam", "eggs")
    is List<*> -> parameterNamesExpression.filterIsInstance<String>()
    else -> emptyList()
  }

  if (valuesExpression == null) {
    //No type info available
    return parameterNames.map { PyTestParameter(it) }
  }

  //Type information may be available

  val parameterTypes = arrayOfNulls<PyType?>(parameterNames.size)

  val iteratedItemType = valuesExpression.iteratedItemType

  when (iteratedItemType) {
    is PyUnionType -> {
      //Could be union of tuples
      val members = iteratedItemType.members
      for (i in 0 until parameterTypes.size) {
        parameterTypes[i] = PyUnionType.union(members.map { (it as? PyTupleType)?.getElementType(i) })
      }
    }
    is PyTupleType -> iteratedItemType.elementTypes.forEachIndexed { i, type -> parameterTypes[i] = type }
    !is PyCollectionType -> parameterTypes.fill(iteratedItemType)
  }

  return parameterNames.mapIndexed { i, name -> PyTestParameter(name, parameterTypes[i]) }
}


/**
 * Parameter passed from parametrized
 */
internal data class PyTestParameter(val name: String, val type: PyType? = null)

/**
 * @return List<String> if test function decorated with parametrize -- return parameter names
 */
internal fun PyFunction.getParametersOfParametrized(evalContext: TypeEvalContext): List<PyTestParameter> {
  val decoratorList = decoratorList ?: return emptyList()
  if (!isTestElement(this, ThreeState.NO, evalContext)) {
    return emptyList()
  }
  return decoratorList.decorators
    .filter { it.name == "parametrize" }
    .flatMap { getParametersFromDecorator(it, evalContext) }

}

