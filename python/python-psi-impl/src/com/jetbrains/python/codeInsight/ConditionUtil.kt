// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ConditionUtil")

package com.jetbrains.python.codeInsight

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.parents
import com.intellij.util.IncorrectOperationException
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyPsiUtils

/**
 * Conditional expressions utility
 */

private val comparisonStrings = hashMapOf(
  PyTokenTypes.LT to "<",
  PyTokenTypes.GT to ">",
  PyTokenTypes.EQEQ to "==",
  PyTokenTypes.LE to "<=",
  PyTokenTypes.GE to ">=",
  PyTokenTypes.NE to "!=",
  PyTokenTypes.NE_OLD to "<>"
)

private val invertedComparisons = hashMapOf(
  PyTokenTypes.LT to PyTokenTypes.GE,
  PyTokenTypes.GT to PyTokenTypes.LE,
  PyTokenTypes.EQEQ to PyTokenTypes.NE,
  PyTokenTypes.LE to PyTokenTypes.GT,
  PyTokenTypes.GE to PyTokenTypes.LT,
  PyTokenTypes.NE to PyTokenTypes.EQEQ,
  PyTokenTypes.NE_OLD to PyTokenTypes.EQEQ
)

private val validConditionalOperators = hashSetOf(
  PyTokenTypes.IS_KEYWORD,
  PyTokenTypes.IN_KEYWORD,
  PyTokenTypes.NOT_KEYWORD,
  PyTokenTypes.OR_KEYWORD,
  PyTokenTypes.AND_KEYWORD,
  *comparisonStrings.keys.toTypedArray()
)

fun findComparisonNegationOperators(expression: PyBinaryExpression?): Pair<String, String>? {
  val comparisonExpression = findComparisonExpression(expression) ?: return null
  return comparisonStrings.getValue(comparisonExpression.operator) to
    comparisonStrings.getValue(invertedComparisons.getValue(comparisonExpression.operator))
}

fun findComparisonExpression(expression: PyBinaryExpression?): PyBinaryExpression? {
  var comparisonExpression = expression
  while (comparisonExpression != null) {
    if (comparisonStrings.containsKey(comparisonExpression.operator)) {
      return comparisonExpression
    }
    comparisonExpression = PsiTreeUtil.getParentOfType(comparisonExpression, PyBinaryExpression::class.java)
  }
  return null
}

fun negateComparisonExpression(project: Project, file: PsiFile, expression: PyBinaryExpression?): PsiElement? {
  val comparisonExpression = findComparisonExpression(expression) ?: return null

  val level = LanguageLevel.forElement(file)
  val elementGenerator = PyElementGenerator.getInstance(project)

  val parent = findNonParenthesizedExpressionParent(comparisonExpression)
  val invertedOperator = invertedComparisons.getValue(comparisonExpression.operator)
  val invertedExpression = elementGenerator.createBinaryExpression(
    comparisonStrings.getValue(invertedOperator),
    comparisonExpression.leftExpression,
    comparisonExpression.rightExpression!!)

  if (parent is PyPrefixExpression && parent.operator === PyTokenTypes.NOT_KEYWORD) {
    return parent.replace(invertedExpression)
  }
  else {
    return comparisonExpression.replace(elementGenerator.createExpressionFromText(level, "not " + invertedExpression.text))
  }
}

fun isValidConditionExpression(expression: PyExpression): Boolean {
  if (expression is PyParenthesizedExpression) {
    return expression.containedExpression != null && isValidConditionExpression(expression.containedExpression!!)
  }

  if (expression is PyPrefixExpression && expression.operator == PyTokenTypes.NOT_KEYWORD) {
    return expression.operand != null
  }

  if (expression is PyBinaryExpression) {
    if (expression.leftExpression == null || expression.rightExpression == null) {
      return false
    }

    if (expression.operator == PyTokenTypes.OR_KEYWORD || expression.operator == PyTokenTypes.AND_KEYWORD) {
      return expression.leftExpression != null &&
             expression.rightExpression != null &&
             isValidConditionExpression(expression.leftExpression) &&
             isValidConditionExpression(expression.rightExpression!!)
    }

    return expression.operator in validConditionalOperators
  }

  return true
}

fun getInvertedConditionExpression(project: Project, file: PsiFile, expression: PyExpression): PyExpression {
  val level = LanguageLevel.forElement(file)
  val elementGenerator = PyElementGenerator.getInstance(project)
  return getInvertedConditionExpression(project, file, level, elementGenerator, expression, true)
}

private fun getInvertedConditionExpression(
  project: Project,
  file: PsiFile,
  level: LanguageLevel,
  generator: PyElementGenerator,
  expression: PyExpression,
  isTopLevelExpression: Boolean): PyExpression {
  if (expression is PyParenthesizedExpression) {
    return getInvertedConditionExpression(
      project, file, level, generator, expression.containedExpression!!, isTopLevelExpression)
  }

  if (expression is PyPrefixExpression && expression.operator == PyTokenTypes.NOT_KEYWORD) {
    val invertedExpression = expression.operand!!
    return if (isTopLevelExpression && !requiresParentheses(invertedExpression))
      PyPsiUtils.flattenParens(invertedExpression)!!.copy() as PyExpression
    else
      invertedExpression.copy() as PyExpression
  }

  if (expression !is PyBinaryExpression) {
    val expressionBuilder = StringBuilder("not ")
    if (expression is PyAssignmentExpression || requiresParentheses(expression)) {
      expressionBuilder.append("(")
      expressionBuilder.append(expression.text)
      expressionBuilder.append(")")
    }
    else {
      expressionBuilder.append(expression.text)
    }
    return generator.createExpressionFromText(level, expressionBuilder.toString())
  }

  if (expression.operator == PyTokenTypes.IS_KEYWORD) {
    val isNegative = expression.node.findChildByType(PyTokenTypes.NOT_KEYWORD) != null
    return generator.createBinaryExpression(
      if (isNegative) "is" else "is not",
      expression.leftExpression,
      expression.rightExpression!!)
  }

  if (expression.operator == PyTokenTypes.IN_KEYWORD) {
    return generator.createBinaryExpression(
      "not in",
      expression.leftExpression,
      expression.rightExpression!!)
  }

  if (expression.operator == PyTokenTypes.NOT_KEYWORD) {
    if (expression.node.findChildByType(PyTokenTypes.IN_KEYWORD) == null) {
      throw IncorrectOperationException("Unexpected NOT binary expression")
    }
    return generator.createBinaryExpression(
      "in",
      expression.leftExpression,
      expression.rightExpression!!)
  }

  if (expression.operator == PyTokenTypes.OR_KEYWORD) {
    return generator.createBinaryExpression(
      "and",
      getInvertedConditionExpression(
        project, file, level, generator, expression.leftExpression, false),
      getInvertedConditionExpression(
        project, file, level, generator, expression.rightExpression!!,
        false))
  }

  if (expression.operator == PyTokenTypes.AND_KEYWORD) {
    val adjacentExpressions = mutableListOf<PyExpression>()
    var currentExpression = expression
    while (currentExpression is PyBinaryExpression && currentExpression.operator == PyTokenTypes.AND_KEYWORD) {
      adjacentExpressions.add(currentExpression.rightExpression!!)
      currentExpression = currentExpression.leftExpression
    }
    adjacentExpressions.add(currentExpression)
    val invertedExpressions = adjacentExpressions.asReversed().map {
      getInvertedConditionExpression(
        project, file, level, generator, it, false)
    }
    return createOrExpression(level, generator, invertedExpressions, isTopLevelExpression)
  }

  if (comparisonStrings.containsKey(expression.operator)) {
    val chainedExpressions = mutableListOf<PyExpression>()
    var currentExpression = expression
    while (currentExpression is PyBinaryExpression && comparisonStrings.containsKey(currentExpression.operator)) {
      val leftExpression = currentExpression.leftExpression
      // The left operand forms a chained comparison, e.g.
      // `left.left <= left.right < right`, which is equivalent to
      // `(left.left <= left.right) and (left.right < right)`
      // so it should be inverted as
      // `not (left.left <= left.right) or not (left.right < right)` or, after simplification,
      // `left.left > left.right or left.right >= right`
      val newLeftExpression = if (leftExpression is PyBinaryExpression && comparisonStrings.containsKey(leftExpression.operator)) {
        leftExpression.rightExpression ?: leftExpression
      }
      else {
        leftExpression
      }
      val invertedOperator = invertedComparisons.getValue(currentExpression.operator)
      val invertedExpression = generator.createBinaryExpression(
        comparisonStrings.getValue(invertedOperator), newLeftExpression, currentExpression.rightExpression!!)
      chainedExpressions.add(invertedExpression)
      currentExpression = currentExpression.leftExpression
    }

    return createOrExpression(level, generator, chainedExpressions.asReversed(), isTopLevelExpression)
  }

  throw IncorrectOperationException("Is not a condition")
}

private fun findNonParenthesizedExpressionParent(element: PsiElement): PsiElement {
  var parent = element.parent
  while (parent is PyParenthesizedExpression) {
    parent = parent.getParent()
  }
  return parent
}

private fun requiresParentheses(element: PsiElement): Boolean {
  val allWhitespacesAreParenthesized = element.descendantsOfType<PsiWhiteSpace>().map { whitespace ->
    whitespace.parents(false).takeWhile { it != element }.firstOrNull {
      it is PyParenthesizedExpression || it is PyArgumentList
    }
  }.all { it != null }
  if (allWhitespacesAreParenthesized) {
    return false
  }

  var result = true
  var hasTrailingBackslash = false
  for (c in element.text) {
    when {
      StringUtil.isLineBreak(c) -> {
        result = result && hasTrailingBackslash
        hasTrailingBackslash = false
      }
      c == '\\' -> {
        hasTrailingBackslash = true
      }
      !StringUtil.isWhiteSpace(c) -> {
        hasTrailingBackslash = false
      }
    }
  }
  return !result
}

private fun createOrExpression(
  level: LanguageLevel, generator: PyElementGenerator, expressions: Iterable<PyExpression>, isTopLevelExpression: Boolean): PyExpression {
  val result = StringBuilder()

  val requiresParentheses = !isTopLevelExpression && expressions.count() > 1
  if (requiresParentheses) {
    result.append("(")
  }

  expressions.forEachIndexed { i, expression ->
    if (i > 0) {
      result.append(" or ")
    }
    result.append(expression.text)
  }

  if (requiresParentheses) {
    result.append(")")
  }

  return generator.createExpressionFromText(level, result.toString())
}
