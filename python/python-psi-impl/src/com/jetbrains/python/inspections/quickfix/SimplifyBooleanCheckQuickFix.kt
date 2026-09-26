// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.impl.PyPsiUtils

class SimplifyBooleanCheckQuickFix(binaryExpression: PyBinaryExpression) : PsiUpdateModCommandQuickFix() {
  private val myReplacementText = binaryExpression.createReplacementText()

  override fun getName() = PyPsiBundle.message("QFIX.simplify.boolean.expression", myReplacementText)

  override fun getFamilyName() = PyPsiBundle.message("QFIX.NAME.simplify.boolean.expression")

  public override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    PyPsiUtils.assertValid(element)
    if (!element.isValid || element !is PyBinaryExpression) {
      return
    }
    val elementGenerator = PyElementGenerator.getInstance(project)
    element.replace(elementGenerator.createExpressionFromText(LanguageLevel.forElement(element), myReplacementText))
  }

  companion object {
    private val PyExpression.isTrue get() = "True" == text

    private val PyExpression.isFalse get() = "False" == text

    private val PyExpression.isFalsey get() = isFalse || isZero || isEmpty

    private val PyExpression.isZero get() = "0" == text

    private val PyExpression.isEmpty get() = "[]" == text


    private fun PyBinaryExpression.createReplacementText(): String {
      val leftExpression = leftExpression
      val rightExpression = rightExpression!!
      val positiveCondition =
        (operator === PyTokenTypes.EQEQ || isOperator(PyNames.IS)) xor (leftExpression.isFalsey || rightExpression.isFalsey)
      val resultExpression = if (leftExpression.isTrue || leftExpression.isFalsey)
        rightExpression
      else leftExpression
      return (if (positiveCondition) "" else "not ") + resultExpression.text
    }
  }
}
