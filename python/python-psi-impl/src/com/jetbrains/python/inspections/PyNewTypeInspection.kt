package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.PyTypingNewType

class PyNewTypeInspection : PyInspection() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return object : PyInspectionVisitor(holder, getContext(session)) {
      override fun visitPyTargetExpression(node: PyTargetExpression) {
        val assignedValue = node.findAssignedValue()
        if (assignedValue !is PyCallExpression) return

        val isNewTypeCall = assignedValue
          .multiResolveCalleeFunction(resolveContext)
          .any { it.qualifiedName == PyTypingTypeProvider.NEW_TYPE }

        if (isNewTypeCall) {
          val newTypeName = PyResolveUtil.resolveStrArgument(assignedValue, 0, "name")
          val targetName = node.name
          if (targetName != newTypeName) {
            registerProblem(node.nameIdentifier,
                            PyPsiBundle.message("INSP.NAME.new.type.variable.name.does.not.match.new.type.name", targetName, newTypeName))
          }
        }
      }

      override fun visitPyClass(node: PyClass) {
        for (superClassExpression in node.superClassExpressions) {
          val superClassType = myTypeEvalContext.getType(superClassExpression)
          if (superClassType is PyTypingNewType) {
            registerProblem(superClassExpression,
                            PyPsiBundle.message("INSP.NAME.new.type.cannot.be.subclassed", superClassType.name),
                            ProblemHighlightType.GENERIC_ERROR)
          }
        }
      }
    }
  }
}