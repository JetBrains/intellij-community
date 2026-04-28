/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.inspections.quickfix.SimplifyBooleanCheckQuickFix
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyConditionalStatementPart
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.isNoneType

class PySimplifyBooleanCheckInspection : PyInspection() {
  var ignoreComparisonToZero: Boolean = true

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor {
    return Visitor(holder, ignoreComparisonToZero, PyInspectionVisitor.getContext(session))
  }

  override fun getOptionsPane(): OptPane {
    return OptPane.pane(
      OptPane.checkbox(
        "ignoreComparisonToZero",
        PyPsiBundle.message("INSP.simplify.boolean.check.ignore.comparison.to.zero")
      )
    )
  }

  private class Visitor(
    holder: ProblemsHolder?,
    private val myIgnoreComparisonToZero: Boolean,
    context: TypeEvalContext,
  ) : PyInspectionVisitor(holder, context) {
    override fun visitPyConditionalStatementPart(node: PyConditionalStatementPart) {
      super.visitPyConditionalStatementPart(node)
      node.condition?.accept(PyBinaryExpressionVisitor(holder, myTypeEvalContext, myIgnoreComparisonToZero))
    }
  }

  private class PyBinaryExpressionVisitor(
    holder: ProblemsHolder?,
    context: TypeEvalContext,
    private val myIgnoreComparisonToZero: Boolean,
  ) : PyInspectionVisitor(holder, context) {
    override fun visitPyBinaryExpression(node: PyBinaryExpression) {
      super.visitPyBinaryExpression(node)
      val operator = node.operator
      val leftExpression = node.leftExpression
      val rightExpression = node.rightExpression
      if (rightExpression == null || rightExpression is PyBinaryExpression ||
          leftExpression is PyBinaryExpression
      ) {
        return
      }

      val leftType = myTypeEvalContext.getType(leftExpression)
      val rightType = myTypeEvalContext.getType(rightExpression)

      val isIdentity = node.isOperator(PyNames.IS) || node.isOperator("isnot")

      // if no type and `is`, then it's unsafe
      if ((leftType == null || rightType == null) && isIdentity) {
        return
      }

      // because we are comparing to literal values, there will only ever be a union on one side
      val unionMembers = (leftType as? PyUnionType)?.members ?: (rightType as? PyUnionType)?.members

      val isOptional = unionMembers?.any { it.isNoneType } == true

      // if the union is `X | Y | None` or just `X | Y` then it is unsafe to simplify
      if (isOptional && unionMembers.size > 2 || !isOptional && unionMembers != null) {
        return
      }

      if (!isIdentity && !PyTokenTypes.EQUALITY_OPERATIONS.contains(operator)) {
        return
      }

      val compareWithZero = !myIgnoreComparisonToZero && node.operandsEqualTo(setOf("0"))
      val compareWithFalsey = node.operandsEqualTo(listOf(PyNames.FALSE, "[]"))

      // 'x is falsey' where `x` is `T | None`, then it is unsafe to simplify
      //  because the falsey value will evaluate to `False` which will be ambiguous with `None`
      if (isOptional && (compareWithFalsey || compareWithZero)) {
        return
      }

      if (node.operandsEqualTo(COMPARISON_LITERALS) || compareWithZero) {
        registerProblem(node)
      }
    }

    fun registerProblem(binaryExpression: PyBinaryExpression) {
      registerProblem(
        binaryExpression, PyPsiBundle.message("INSP.expression.can.be.simplified"),
        SimplifyBooleanCheckQuickFix(binaryExpression)
      )
    }

    companion object {
      private fun PyBinaryExpression.operandsEqualTo(literals: Collection<String>): Boolean {
        val leftExpressionText = leftExpression.text
        val rightExpressionText = rightExpression?.text
        return literals.any { it == leftExpressionText || it == rightExpressionText }
      }
    }
  }
}

private val COMPARISON_LITERALS = listOf("True", "False", "[]")