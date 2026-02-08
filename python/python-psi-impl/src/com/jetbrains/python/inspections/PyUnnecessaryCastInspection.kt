package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeUtil

class PyUnnecessaryCastInspection : PyInspection() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return object : PyInspectionVisitor(holder, getContext(session)) {
      override fun visitPyCallExpression(callExpression: PyCallExpression) {
        val callees = callExpression.multiResolveCalleeFunction(resolveContext)
        val isCastCall = callees.any {
          (it as? PyFunction)?.qualifiedName == PyTypingTypeProvider.CAST ||
          (it as? PyFunction)?.qualifiedName == PyTypingTypeProvider.CAST_EXT
        }
        if (!isCastCall) return

        val args = callExpression.getArguments()
        if (args.size != 2) return
        val targetTypeRef = PyTypingTypeProvider.getType(args[0], myTypeEvalContext)
        val targetType = Ref.deref(targetTypeRef)
        val actualType: PyType? = myTypeEvalContext.getType(args[1])

        if (!PyTypeUtil.isSameType(targetType, actualType, myTypeEvalContext)) return
        val toName = PythonDocumentationProvider.getTypeName(targetType, myTypeEvalContext)
        registerProblem(
          callExpression,
          PyPsiBundle.message(
            "INSP.unnecessary.cast.message",
            toName
          ),
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
          null,
          TextRange(0, callExpression.arguments[0].nextSibling.endOffset - callExpression.startOffset),
          RemoveUnnecessaryCastQuickFix(),
        )
        registerProblem(
          callExpression,
          PyPsiBundle.message(
            "INSP.unnecessary.cast.message",
            toName
          ),
          ProblemHighlightType.INFORMATION,
          null,
          RemoveUnnecessaryCastQuickFix(),
        )
      }
    }
  }
}

private class RemoveUnnecessaryCastQuickFix : PsiUpdateModCommandQuickFix() {
  override fun getFamilyName(): String = PyPsiBundle.message("QFIX.remove.cast.call")

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val call = element as? PyCallExpression ?: return
    val args = call.getArguments()
    if (args.size != 2) return
    val expr = args[1] ?: return
    call.replace(expr.copy())
  }
}
