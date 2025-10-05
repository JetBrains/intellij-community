package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.*

class PyAssertTypeInspection : PyInspection() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return object : PyInspectionVisitor(holder, getContext(session)) {
      override fun visitPyCallExpression(callExpression: PyCallExpression) {
        val callable = callExpression.multiResolveCalleeFunction(resolveContext).singleOrNull()
        if (callable is PyFunction && PyTypingTypeProvider.ASSERT_TYPE == callable.qualifiedName) {
          val arguments = callExpression.getArguments()
          if (arguments.size == 2) {
            val actualType = myTypeEvalContext.getType(arguments[0])
            val expectedType = Ref.deref(PyTypingTypeProvider.getType(arguments[1], myTypeEvalContext))
            if (!isSame(actualType, expectedType, myTypeEvalContext)) {
              val expectedName = PythonDocumentationProvider.getVerboseTypeName(expectedType, myTypeEvalContext)
              val actualName = PythonDocumentationProvider.getTypeName(actualType, myTypeEvalContext)
              registerProblem(arguments[0],
                              PyPsiBundle.message("INSP.assert.type.expected.type.got.type.instead", expectedName, actualName))
            }
          }
        }
      }
    }
  }
}

private fun isSame(type1: PyType?, type2: PyType?, context: TypeEvalContext): Boolean {
  if (type1 is PyCallableType && (type1 !is PyClassLikeType) &&
      type2 is PyCallableType && (type2 !is PyClassLikeType)) {
    val returnType1 = type1.getReturnType(context)
    val returnType2 = type2.getReturnType(context)
    if (!isSame(returnType1, returnType2, context)) {
      return false
    }

    val parameters1 = getCallableParameters(type1, context)
    val parameters2 = getCallableParameters(type2, context)
    if (parameters1 == null || parameters2 == null) {
      return parameters1 == parameters2
    }

    if (parameters1.posOnly.size != parameters2.posOnly.size) {
      return false
    }
    repeat(parameters1.posOnly.size) { index ->
      val parameter1 = parameters1.posOnly[index]
      val parameter2 = parameters2.posOnly[index]
      if (!isSame(parameter1.getType(context), parameter2.getType(context), context)) {
        return false
      }
    }

    if (parameters1.standard.size != parameters2.standard.size) {
      return false
    }
    repeat(parameters1.standard.size) { index ->
      val parameter1 = parameters1.standard[index]
      val parameter2 = parameters2.standard[index]
      if (parameter1.name != parameter2.name) {
        return false
      }
      if (parameter1.isPositionalContainer != parameter2.isPositionalContainer) {
        return false
      }
      if (!isSame(parameter1.getType(context), parameter2.getType(context), context)) {
        return false
      }
    }

    val keywordOnlyParameters2 = mutableMapOf<String?, PyCallableParameter>()
    parameters2.keywordOnly.associateByTo(keywordOnlyParameters2) { it.name }

    for (parameter1 in parameters1.keywordOnly) {
      val parameter2 = keywordOnlyParameters2.remove(parameter1.name)
      if (parameter2 == null) {
        return false
      }
      if (!isSame(parameter1.getType(context), parameter2.getType(context), context)) {
        return false
      }
    }
    return keywordOnlyParameters2.isEmpty()
  }
  return type1 == type2
}

private fun getCallableParameters(callableType: PyCallableType, context: TypeEvalContext): CallableParameters? {
  val parameters = callableType.getParameters(context) ?: return null

  val posOnlyParameters: List<PyCallableParameter>
  val standardAndKeywordOnlyParameters: List<PyCallableParameter>

  val posOnlySeparatorIndex = parameters.indexOfFirst { it.isPositionOnlySeparator }
  if (posOnlySeparatorIndex == -1) {
    // TODO If CallableType is inferred from a 'Callable[]' type hint, there is no terminating '/' parameter.
    // Check whether all parameters have no name then.
    if (parameters.all { it.name == null }) {
      posOnlyParameters = parameters
      standardAndKeywordOnlyParameters = emptyList()
    }
    else {
      posOnlyParameters = emptyList()
      standardAndKeywordOnlyParameters = parameters
    }
  }
  else {
    posOnlyParameters = parameters.subList(0, posOnlySeparatorIndex)
    standardAndKeywordOnlyParameters = parameters.subList(posOnlySeparatorIndex + 1, parameters.size)
  }

  val kwargSeparatorIndex = standardAndKeywordOnlyParameters.indexOfFirst { it.isKeywordOnlySeparator }
  return if (kwargSeparatorIndex == -1) {
    CallableParameters(posOnlyParameters, standardAndKeywordOnlyParameters, emptyList())
  }
  else {
    CallableParameters(
      posOnlyParameters,
      standardAndKeywordOnlyParameters.subList(0, kwargSeparatorIndex),
      standardAndKeywordOnlyParameters.subList(kwargSeparatorIndex + 1, standardAndKeywordOnlyParameters.size)
    )
  }
}

private class CallableParameters(
  val posOnly: List<PyCallableParameter>,
  val standard: List<PyCallableParameter>,
  val keywordOnly: List<PyCallableParameter>,
)