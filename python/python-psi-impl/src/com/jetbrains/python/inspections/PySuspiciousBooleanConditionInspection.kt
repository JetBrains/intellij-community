// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyAssertStatement
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyConditionalExpression
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyIfStatement
import com.jetbrains.python.psi.PyPrefixExpression
import com.jetbrains.python.psi.PyWhileStatement
import com.jetbrains.python.psi.types.PyABCUtil
import com.jetbrains.python.psi.types.TypeEvalContext


class PySuspiciousBooleanConditionInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return Visitor(holder, PyInspectionVisitor.getContext(session))
  }

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyIfStatement(node: PyIfStatement) {
      super.visitPyIfStatement(node)
      checkBooleanContext(node.ifPart.condition)
      node.elifParts.forEach { checkBooleanContext(it.condition) }
    }

    override fun visitPyWhileStatement(node: PyWhileStatement) {
      super.visitPyWhileStatement(node)
      checkBooleanContext(node.whilePart.condition)
    }

    override fun visitPyConditionalExpression(node: PyConditionalExpression) {
      super.visitPyConditionalExpression(node)
      checkBooleanContext(node.condition)
    }

    override fun visitPyBinaryExpression(node: PyBinaryExpression) {
      super.visitPyBinaryExpression(node)
      // Check operands of 'and' and 'or' boolean operators
      if (node.operator == PyTokenTypes.AND_KEYWORD || node.operator == PyTokenTypes.OR_KEYWORD) {
        checkExpression(node.leftExpression)
        checkExpression(node.rightExpression)
      }
    }

    override fun visitPyPrefixExpression(node: PyPrefixExpression) {
      super.visitPyPrefixExpression(node)
      // Check operand of 'not' operator
      if (node.operator == PyTokenTypes.NOT_KEYWORD) {
        checkExpression(node.operand)
      }
    }

    override fun visitPyAssertStatement(node: PyAssertStatement) {
      super.visitPyAssertStatement(node)
      // Check the first argument (the condition being asserted)
      val arguments = node.arguments
      if (arguments.isNotEmpty()) {
        checkExpression(arguments[0])
      }
    }

    private fun checkBooleanContext(condition: PyExpression?) {
      condition ?: return
      checkExpression(condition)
    }

    private fun checkExpression(expr: PyExpression?) {
      expr ?: return

      // Don't flag awaited expressions - they're already being awaited
      if (expr is PyPrefixExpression && expr.operator == PyTokenTypes.AWAIT_KEYWORD) return

      val type = myTypeEvalContext.getType(expr) ?: return

      // Check if the type is a coroutine/awaitable
      if (PyABCUtil.isSubtype(type, PyNames.AWAITABLE, myTypeEvalContext)) {
        registerProblem(expr, PyPsiBundle.message("INSP.suspicious.boolean.condition.coroutine"), PyAddAwaitQuickFix())
      }
    }
  }

  private class PyAddAwaitQuickFix : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName() = PyPsiBundle.message("QFIX.add.await")

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
      if (element !is PyExpression) return

      val generator = PyElementGenerator.getInstance(project)
      val awaitExpr = generator.createExpressionFromText(
        LanguageLevel.forElement(element),
        "${PyNames.AWAIT} ${element.text}"
      )
      element.replace(awaitExpr)
    }
  }
}
