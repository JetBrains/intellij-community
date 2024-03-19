// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.JKJavaPrimitiveType
import org.jetbrains.kotlin.nj2k.types.asPrimitiveType

class SimplifyNegatedBinaryExpressionConversion(context: NewJ2kConverterContext) : RecursiveConversion(context) {
    context(KtAnalysisSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKPrefixExpression || element.operator.token != JKOperatorToken.EXCL) return recurse(element)
        val expression = element.expression
        if (expression !is JKParenthesizedExpression) return recurse(element)
        val innerExpression = expression.expression
        val hasParenthesis = hasParenthesis(element.parent)
        if (innerExpression is JKIsExpression) {
            expression::expression.detached()
            innerExpression.negateSymbol = true
            return if (hasParenthesis) recurse(JKParenthesizedExpression(innerExpression)) else recurse(innerExpression)
        }
        if (innerExpression is JKBinaryExpression && isAbleToBeSimplified(innerExpression)) {
            val negatedToken = getNegatedToken(innerExpression.operator.token) ?: return recurse(element)
            innerExpression::left.detached()
            innerExpression::right.detached()
            val binaryExpression = JKBinaryExpression(
                innerExpression.left, innerExpression.right, JKKtOperatorImpl(
                    negatedToken,
                    typeFactory.types.nullableAny
                )
            )
            return if (hasParenthesis) recurse(JKParenthesizedExpression(binaryExpression)) else recurse(binaryExpression)
        }
        return recurse(element)
    }

    private fun getNegatedToken(operator: JKOperatorToken): JKOperatorToken? {
        return when (operator) {
            JKOperatorToken.EQEQ -> JKOperatorToken.EXCLEQ
            JKOperatorToken.EXCLEQ -> JKOperatorToken.EQEQ
            JKOperatorToken.EQEQEQ -> JKOperatorToken.EXCLEQEQEQ
            JKOperatorToken.EXCLEQEQEQ -> JKOperatorToken.EQEQEQ
            JKOperatorToken.LT -> JKOperatorToken.GTEQ
            JKOperatorToken.GTEQ -> JKOperatorToken.LT
            JKOperatorToken.GT -> JKOperatorToken.LTEQ
            JKOperatorToken.LTEQ -> JKOperatorToken.GT
            else -> null
        }
    }

    private fun isAbleToBeSimplified(element: JKBinaryExpression): Boolean {
        val operatorToken = element.operator.token
        if (operatorToken != JKOperatorToken.LT && operatorToken != JKOperatorToken.LTEQ
            && operatorToken != JKOperatorToken.GT && operatorToken != JKOperatorToken.GTEQ
        ) return true
        val leftType = element.left.calculateType(typeFactory)?.asPrimitiveType()
        val rightType = element.right.calculateType(typeFactory)?.asPrimitiveType()
        return leftType != JKJavaPrimitiveType.FLOAT && leftType != JKJavaPrimitiveType.DOUBLE
                && rightType != JKJavaPrimitiveType.FLOAT && rightType != JKJavaPrimitiveType.DOUBLE
    }

    private fun hasParenthesis(element: JKElement?): Boolean = when (element) {
        is JKIfElseStatement, is JKReturnStatement -> false
        else -> true
    }
}

