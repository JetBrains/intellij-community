package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.codeInsight.parseDataclassParameters
import com.jetbrains.python.codeInsight.stdlib.PyDataclassTransformType
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyTargetExpression
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
        }

        true
      }
    }
  }
}
