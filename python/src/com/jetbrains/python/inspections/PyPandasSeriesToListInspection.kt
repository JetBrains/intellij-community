// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyLiteralStringType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyPandasSeriesToListInspection : PyInspection() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return Visitor(holder, PyInspectionVisitor.getContext(session))
  }

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyCallExpression(node: PyCallExpression) {
      if ((PsiTreeUtil.hasErrorElements(node))) return
      if (node.callee?.text != "list" || node.arguments.size != 1) return
      val argument = node.arguments.single()
      if (argument !is PyQualifiedExpression) return
      if (argument.referencedName != "values") return

      val qualifier = argument.qualifier ?: return

      if (hasSeriesType(qualifier, myTypeEvalContext)) {
        registerProblem(node, PyPsiBundle.message("INSP.pandas.series.values.replace.with.tolist"), DSPandasSeriesToListQuickFix())
      }
    }

    /**
     * Checks whether expression possibly has type Series.
     */
    private fun hasSeriesType(expression: PyExpression, context: TypeEvalContext): Boolean {
      if (expression is PySubscriptionExpression) {
        val type = context.getType(expression.indexExpression)
        return type.isPyClassWithName("str") || type is PyLiteralStringType
      }

      val expressionType = context.getType(expression)
      if (expressionType.isPyClassWithName("Series")) return true

      if (expressionType != null) return false
      return isQualifiedDataframeCall(expression, context)
    }

    private fun isQualifiedDataframeCall(expression: PyExpression, context: TypeEvalContext): Boolean {
      if (expression !is PyQualifiedExpression) return false
      return context.getType(expression.qualifier).isPyClassWithName("DataFrame")
    }
  }

  private class DSPandasSeriesToListQuickFix : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName() = PyPsiBundle.message("QFIX.pandas.series.values.replace.with.tolist")

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
      val functionCall = element as? PyCallExpression ?: return

      val argument = functionCall.arguments.singleOrNull() as? PyQualifiedExpression ?: return
      val argumentQualifier = argument.qualifier ?: return

      val toListCallString = argumentQualifier.text + ".to_list()"

      val toListCallStatement = PyElementGenerator.getInstance(project).createFromText(
        LanguageLevel.forElement(functionCall),
        PyExpressionStatement::class.java,
        toListCallString
      )

      functionCall.replace(toListCallStatement.expression)
    }
  }
}

private fun PyType?.isPyClassWithName(typeName: String) =
  this is PyClassType && name == typeName


private fun TypeEvalContext.getType(element: PyTypedElement?): PyType? {
  if (element == null) return null
  return getType(element)
}
