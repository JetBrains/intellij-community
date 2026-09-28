package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.parseDataclassParameters
import com.jetbrains.python.codeInsight.stdlib.PyDataclassTransformResolver
import com.jetbrains.python.codeInsight.stdlib.PyDataclassTransformType
import com.jetbrains.python.inspections.PyInspectionMessages.CodifiedParam
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.TypeEvalContext

class PyDataclassTransformInspection : PyInspection() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor {
    val context = PyInspectionVisitor.getContext(session)
    if (context.usesExternalTypeEngine) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return Visitor(holder, context)
  }

  class Visitor(holder: ProblemsHolder?, context: TypeEvalContext) : PyDataclassVisitor(holder, context) {

    override fun visitPyClass(node: PyClass) {
      val dataclassParameters = parseDataclassParameters(node, myTypeEvalContext)?.takeIf { it.type == PyDataclassTransformType } ?: return

      processDataclassParameters(node, dataclassParameters)

      node.processClassLevelDeclarations { element, _ ->
        if (element is PyTargetExpression) {
          processFieldFunctionCall(node, dataclassParameters, element)
          processConverterDefault(element)
        }

        true
      }
    }

    /** Reports a `default`, `default_factory` or `factory` value that the field's `converter` cannot accept. */
    private fun processConverterDefault(field: PyTargetExpression) {
      val converterInputType = PyDataclassTransformResolver.getConverterInputType(field, myTypeEvalContext) ?: return
      val call = field.findAssignedValue() as? PyCallExpression ?: return

      call.getKeywordArgument("default")?.let { default ->
        checkConverterInput(converterInputType, myTypeEvalContext.getType(default), default)
      }
      for (name in listOf("default_factory", "factory")) {
        val factory = call.getKeywordArgument(name) ?: continue
        val defaultType = PyCallExpressionHelper.getCallType(myTypeEvalContext.getType(factory), emptyList(), myTypeEvalContext)
        checkConverterInput(converterInputType, defaultType, factory)
      }
    }

    private fun checkConverterInput(expected: PyType, actual: PyType?, anchor: PyExpression) {
      if (PyTypeChecker.match(expected, actual, myTypeEvalContext)) return
      registerProblem(anchor, PyPsiBundle.problemMessage("INSP.type.checker.expected.type.got.type.instead",
                                                         CodifiedParam.ofType(expected, anchor, myTypeEvalContext),
                                                         CodifiedParam.ofType(actual, anchor, myTypeEvalContext)))
    }
  }
}
