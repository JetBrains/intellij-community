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
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyAssertStatement
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyConditionalExpression
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyIfStatement
import com.jetbrains.python.psi.PyPrefixExpression
import com.jetbrains.python.psi.PyWhileStatement
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext


class PySuspiciousBooleanConditionInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return Visitor(holder, PyInspectionVisitor.getContext(session))
  }

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyIfStatement(node: PyIfStatement) {
      node.ifPart.condition.checkBooleanContext()
      node.elifParts.forEach { it.condition.checkBooleanContext() }
    }

    override fun visitPyWhileStatement(node: PyWhileStatement) {
      node.whilePart.condition.checkBooleanContext()
    }

    override fun visitPyConditionalExpression(node: PyConditionalExpression) {
      node.condition.checkBooleanContext()
    }

    override fun visitPyBinaryExpression(node: PyBinaryExpression) {
      if (node.isShortCircuit) {
        node.leftExpression.checkBooleanContext()
        node.rightExpression.checkBooleanContext()
      }
    }

    override fun visitPyPrefixExpression(node: PyPrefixExpression) {
      if (node.operator == PyTokenTypes.NOT_KEYWORD) {
        node.operand.checkBooleanContext()
      }
    }

    override fun visitPyAssertStatement(node: PyAssertStatement) {
      node.arguments.firstOrNull()?.checkBooleanContext()
    }

    private fun PyExpression?.checkBooleanContext() {
      if (this == null) {
        return
      }
      val node = PyPsiUtils.flattenParens(this)
      if (node is PyBinaryExpression && node.isShortCircuit) {
        return
      }
      this.checkExpression()
    }

    private fun PyExpression?.checkExpression() {
      if (this == null) {
        return
      }

      // Don't flag awaited expressions - they're already being awaited
      if (this is PyPrefixExpression && this.operator == PyTokenTypes.AWAIT_KEYWORD) return

      val type = myTypeEvalContext.getType(this) ?: return

      // Check if the type is a coroutine
      // TODO: use `CoroutineType` instead
      if (type is PyClassType && type.classQName == PyTypingTypeProvider.COROUTINE) {
        registerProblem(this, PyPsiBundle.message("INSP.suspicious.boolean.condition.coroutine"), PyAddAwaitQuickFix())
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
