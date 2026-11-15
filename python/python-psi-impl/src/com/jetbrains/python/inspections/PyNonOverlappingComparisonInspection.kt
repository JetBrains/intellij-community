// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.controlflow.PyTypeAssertionEvaluator
import com.jetbrains.python.codeInsight.controlflow.chainAwareLeftExpression
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyDictLiteralExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PySequenceExpression
import com.jetbrains.python.psi.PyStarExpression
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeUtil.asUnionSequence
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

/**
 * Reports an `==`, `!=`, `is`, `is not`, `in` or `not in` comparison that always gives the same result, because the
 * operand types do not overlap. [PyTypeAssertionEvaluator.canNeverCompareTrue] decides whether two types overlap.
 */
@ApiStatus.Internal
class PyNonOverlappingComparisonInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return Visitor(holder, PyInspectionVisitor.getContext(session))
  }

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyBinaryExpression(node: PyBinaryExpression) {
      val rhs = PyPsiUtils.flattenParens(node.rightExpression) ?: return
      val operator = node.operator
      val isIdentity = node.isOperator(PyNames.IS) || node.isOperator(IS_NOT)
      val isEquality = operator == PyTokenTypes.EQEQ || operator == PyTokenTypes.NE || operator == PyTokenTypes.NE_OLD
      val isMembership = operator == PyTokenTypes.IN_KEYWORD || node.isOperator(NOT_IN)
      if (!isIdentity && !isEquality && !isMembership) return
      // In `a == b == c`, the right comparison is `b == c`
      val lhs = node.chainAwareLeftExpression() ?: return
      val leftOperand = PyPsiUtils.flattenParens(lhs) ?: return
      val leftType = myTypeEvalContext.getType(leftOperand)

      val rightType = if (isMembership) getItemType(rhs) ?: return else myTypeEvalContext.getType(rhs)
      val neverTrue = if (isMembership) {
        // A builtin container finds an item by identity or by equality
        canNeverCompareTrue(leftType, rightType, true) && canNeverCompareTrue(leftType, rightType, false)
      }
      else {
        canNeverCompareTrue(leftType, rightType, isIdentity)
      }
      if (!neverTrue) return

      val isNegated = operator == PyTokenTypes.NE || operator == PyTokenTypes.NE_OLD || node.isOperator(IS_NOT) || node.isOperator(NOT_IN)
      val leftName = PythonDocumentationProvider.getTypeName(leftType, myTypeEvalContext)
      val rightName = PythonDocumentationProvider.getTypeName(if (isMembership) myTypeEvalContext.getType(rhs) else rightType,
                                                              myTypeEvalContext)
      val message = when {
        isMembership && isNegated -> PyPsiBundle.message("INSP.non.overlapping.membership.always.true", leftName, rightName)
        isMembership -> PyPsiBundle.message("INSP.non.overlapping.membership.always.false", leftName, rightName)
        isNegated -> PyPsiBundle.message("INSP.non.overlapping.comparison.always.true", leftName, rightName)
        else -> PyPsiBundle.message("INSP.non.overlapping.comparison.always.false", leftName, rightName)
      }
      // The right operand is the last operand of the node
      val range = TextRange(lhs.textRange.startOffset - node.textRange.startOffset, node.textLength)
      registerProblem(node, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, null, range)
    }

    private fun canNeverCompareTrue(left: PyType?, right: PyType?, isIdentity: Boolean): Boolean {
      return PyTypeAssertionEvaluator.canNeverCompareTrue(left, right, isIdentity, myTypeEvalContext)
    }

    /**
     * Returns the union of the item types of the container [expression], or `null` when the container can define its own
     * `__contains__`. A tuple, list or set display gives the type of each item, so that each literal keeps its value.
     */
    private fun getItemType(expression: PyExpression): PyType? {
      if (expression is PySequenceExpression && expression !is PyDictLiteralExpression) {
        val elements = expression.elements
        if (elements.isEmpty() || elements.any { it is PyStarExpression }) return null
        return PyUnionType.union(elements.map { myTypeEvalContext.getType(it) })
      }
      val containerTypes = myTypeEvalContext.getType(expression).asUnionSequence().toList()
      val itemTypes = containerTypes.map { containerType ->
        if (containerType !is PyClassType || containerType.isDefinition || containerType.classQName !in BUILTIN_CONTAINERS) {
          return null
        }
        containerType.iteratedItemType ?: return null
      }
      return PyUnionType.union(itemTypes)
    }
  }

  private companion object {
    const val IS_NOT = "isnot"
    const val NOT_IN = "notin"

    /**
     * The builtin containers whose `__contains__` compares each item by identity and by equality.
     * A subclass that overrides `__contains__` is ignored.
     */
    val BUILTIN_CONTAINERS = setOf(PyNames.FQN.TUPLE, PyNames.FQN.LIST, PyNames.FQN.SET, "builtins.frozenset", PyNames.FQN.DICT)
  }
}
