// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pyTestParametrized

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.util.ThreeState
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.types.*
import com.jetbrains.python.testing.isTestElement
import com.jetbrains.python.testing.pyTestFixtures.getFixtures

/**
 * @return Boolean is parameter provided to function by parametrized decorator
 */
internal fun PyNamedParameter.isParametrized(evalContext: TypeEvalContext) = asParametrized(evalContext) != null


/**
 * Fetch [PyTestParameter] associated with certain param
 */
internal fun PyNamedParameter.asParametrized(evalContext: TypeEvalContext) =
  (ScopeUtil.getScopeOwner(this) as? PyFunction)
    ?.getParametersOfParametrized(evalContext)
    ?.find { it.name == name }


private fun getParametersFromDecorator(decorator: PyDecorator, function: PyFunction, evalContext: TypeEvalContext): List<PyTestParameter> {
  val decoratorArguments = decorator.arguments
  val evaluator = PyEvaluator()
  val parameterNamesExpression = evaluator.evaluate(decoratorArguments.firstOrNull()) ?: return emptyList()
  // (parameterNamesExpression, [valuesExpression])
  val valuesExpression = (decoratorArguments.getOrNull(1) as? PyTypedElement)?.let { evalContext.getType(it) }


  val parameterNames = when (parameterNamesExpression) {
    //For cases when parameters are written as literals "spam,eggs"
    is String -> parameterNamesExpression.split(',').map(String::trim).filterNot { it.isBlank() }
    // For cases when written as tuple or list: ("spam", "eggs")
    is List<*> -> parameterNamesExpression.filterIsInstance<String>()
    else -> emptyList()
  }

  if (valuesExpression == null) {
    //No type info available
    return parameterNames.map { PyTestParameter(it) }
  }

  //Value expression could be scalar
  if (valuesExpression !is PyCollectionType) {
    return parameterNames.map { PyTestParameter(it, valuesExpression) }
  }

  //Type information may be available

  val parameterTypes = arrayOfNulls<PyType?>(parameterNames.size)

  when (val iteratedItemType = valuesExpression.iteratedItemType) {
    is PyUnionType -> {
      //Could be union of tuples
      for (i in parameterTypes.indices) {
        // If iterated elements is tuple -- open it. Otherwise use as union
        parameterTypes[i] = iteratedItemType.map { (it as? PyTupleType)?.getElementType(i) ?: it }
      }
    }
    is PyTupleType -> iteratedItemType.elementTypes.forEachIndexed { i, type -> if (parameterTypes.size > i) parameterTypes[i] = type }
    !is PyCollectionType -> parameterTypes.fill(iteratedItemType)
  }
  // We now have array of param names and array of their types
  // But if indirect=true or indirect=["param"..], we should replace param types with fixture result types

  (decoratorArguments.lastOrNull() as? PyKeywordArgument)?.let {
    patchTypesWithIndirectFixture(parameterNames, parameterTypes, function, evalContext, it)
  }

  return parameterNames.mapIndexed { i, name -> PyTestParameter(name, parameterTypes[i]) }
}

private fun patchTypesWithIndirectFixture(paramNames: List<String>,
                                          paramTypes: Array<PyType?>,
                                          function: PyFunction,
                                          evalContext: TypeEvalContext,
                                          indirectKeyword: PyKeywordArgument) {
  if (indirectKeyword.keyword != "indirect") return
  val indirectParams = when (val expression = PyEvaluator().evaluate(indirectKeyword.valueExpression)) {
    is Boolean -> if (expression) paramNames else emptyList() // indirect=True
    is List<*> -> expression.map { it.toString() } // indirect=["param_name"]
    else -> return
  }
  val module = ModuleUtilCore.findModuleForPsiElement(function) ?: return
  val fixtures = getFixtures(module, function, evalContext).associateBy { it.name }
  for ((i, paramName) in paramNames.withIndex()) {
    if (paramName in indirectParams) {
      paramTypes[i] = fixtures[paramName]?.function?.let { evalContext.getReturnType(it) } // fixture return type
    }
  }
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
    .flatMap { getParametersFromDecorator(it, this, evalContext) }

}

