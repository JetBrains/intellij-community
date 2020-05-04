// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.collectDescendantsOfType
import com.intellij.psi.util.parents
import com.intellij.util.IncorrectOperationException
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyPsiUtils

/**
 * Conditional expressions utility
 *
 * @author Vasya Aksyonov, Alexey.Ivanov
 */
object ConditionUtil {
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

  @JvmStatic
  fun findComparisonNegationOperators(expression: PyBinaryExpression?): Pair<String, String>? {
    val comparisonExpression = findComparisonExpression(expression) ?: return null
    return comparisonStrings.getValue(comparisonExpression.operator) to
      comparisonStrings.getValue(invertedComparisons.getValue(comparisonExpression.operator))
  }

  @JvmStatic
  fun findComparisonExpression(expression: PyBinaryExpression?): PyBinaryExpression? {
    var comparisonExpression = expression
    while (comparisonExpression != null) {
      if (comparisonStrings.containsKey(comparisonExpression.operator)) {
        return comparisonExpression
      }
      comparisonExpression = PsiTreeUtil.getParentOfType(expression, PyBinaryExpression::class.java)
    }
    return null
  }

  @JvmStatic
  fun negateComparisonExpression(project: Project, file: PsiFile, expression: PyBinaryExpression?): PsiElement? {
    val comparisonExpression = findComparisonExpression(expression) ?: return null

    val level = LanguageLevel.forElement(file)
    val elementGenerator = PyElementGenerator.getInstance(project)

    val parent = findNonParenthesizedExpressionParent(comparisonExpression)
    val invertedOperator = invertedComparisons.getValue(comparisonExpression.operator)
    val invertedExpression = elementGenerator.createBinaryExpression(
      comparisonStrings.getValue(invertedOperator),
      comparisonExpression.leftExpression,
      comparisonExpression.rightExpression)

    if (parent is PyPrefixExpression && parent.operator === PyTokenTypes.NOT_KEYWORD) {
      return parent.replace(invertedExpression)
    }
    else {
      return comparisonExpression.replace(elementGenerator.createExpressionFromText(level, "not " + invertedExpression.text))
    }
  }

  @JvmStatic
  fun invertConditionalExpression(project: Project, file: PsiFile, expression: PyExpression): PyExpression {
    val level = LanguageLevel.forElement(file)
    val elementGenerator = PyElementGenerator.getInstance(project)
    val invertedExpression = getInvertedConditionExpression(project, file, level, elementGenerator, expression, true)
    return expression.replace(invertedExpression) as PyExpression
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
        PyPsiUtils.flattenParens(invertedExpression)!!
      else
        invertedExpression
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
        expression.rightExpression)
    }

    if (expression.operator == PyTokenTypes.IN_KEYWORD) {
      return generator.createBinaryExpression(
        "not in",
        expression.leftExpression,
        expression.rightExpression)
    }

    if (expression.operator == PyTokenTypes.NOT_KEYWORD) {
      if (expression.node.findChildByType(PyTokenTypes.IN_KEYWORD) == null) {
        throw IncorrectOperationException("Unexpected NOT binary expression")
      }
      return generator.createBinaryExpression(
        "in",
        expression.leftExpression,
        expression.rightExpression)
    }

    if (comparisonStrings.containsKey(expression.operator)) {
      val invertedOperator = invertedComparisons.getValue(expression.operator)
      return generator.createBinaryExpression(
        comparisonStrings.getValue(invertedOperator),
        expression.leftExpression,
        expression.rightExpression)
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
      val adjacentConjunctions = mutableListOf<PyExpression>()
      var adjacentConjunction = expression.leftExpression
      while (adjacentConjunction is PyBinaryExpression && adjacentConjunction.operator == PyTokenTypes.AND_KEYWORD) {
        adjacentConjunctions.add(adjacentConjunction.leftExpression)
        adjacentConjunctions.add(adjacentConjunction.rightExpression!!)
        adjacentConjunction = adjacentConjunction.leftExpression
      }

      val expressionBuilder = StringBuilder()

      if (!isTopLevelExpression) {
        expressionBuilder.append("(")
      }

      if (adjacentConjunctions.isEmpty()) {
        val invertedExpression = getInvertedConditionExpression(
          project, file, level, generator, expression.leftExpression, false)
        expressionBuilder.append(invertedExpression.text)
        expressionBuilder.append(" or ")
      }
      else {
        adjacentConjunctions.forEach {
          val invertedExpression = getInvertedConditionExpression(
            project, file, level, generator, it, false)
          expressionBuilder.append(invertedExpression.text)
          expressionBuilder.append(" or ")
        }
      }

      val rightExpression = getInvertedConditionExpression(
        project, file, level, generator, expression.rightExpression!!, false)
      expressionBuilder.append(rightExpression.text)

      if (!isTopLevelExpression) {
        expressionBuilder.append(")")
      }

      return generator.createExpressionFromText(level, expressionBuilder.toString())
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
    val allWhitespacesAreParenthesized = element.collectDescendantsOfType<PsiWhiteSpace>().map { whitespace ->
      whitespace.parents.takeWhile { it != element }.firstOrNull {
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
}