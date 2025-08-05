// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.parenthesize
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.asPrimitiveType
import org.jetbrains.kotlin.nj2k.types.isFloatingPoint

/**
 * A code style conversion (disabled in basic mode) that simplifies negated binary expressions
 * of the form `!(a > b)` -> `a <= b`.
 *
 * This is a J2K equivalent of `SimplifyNegatedBinaryExpressionInspection`.
 */
class SimplifyNegatedBinaryExpressionConversion(context: ConverterContext) : RecursiveConversion(context) {
    override fun isEnabledInBasicMode(): Boolean = false

    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKPrefixExpression || element.operator.token != JKOperatorToken.EXCL) return recurse(element)

        val parenthesizedExpression = element.expression as? JKParenthesizedExpression ?: return recurse(element)
        val innerExpression = parenthesizedExpression.expression
        val shouldBeParenthesized = element.parent !is JKIfElseStatement && element.parent !is JKReturnStatement

        if (innerExpression is JKIsExpression) {
            innerExpression.detach(parenthesizedExpression)
            innerExpression.isNegated = true
            return recurse(if (shouldBeParenthesized) innerExpression.parenthesize() else innerExpression)
        }

        if (innerExpression is JKBinaryExpression && innerExpression.canBeSimplified()) {
            val negatedToken = getNegatedToken(innerExpression.operator.token) ?: return recurse(element)
            innerExpression.invalidate()
            val negatedBinaryExpression = JKBinaryExpression(
                innerExpression.left,
                innerExpression.right,
                JKKtOperatorImpl(negatedToken, typeFactory.types.boolean)
            ).withFormattingFrom(parenthesizedExpression).withCommentsFrom(innerExpression)
            return recurse(if (shouldBeParenthesized) negatedBinaryExpression.parenthesize() else negatedBinaryExpression)
        }

        return recurse(element)
    }

    private fun getNegatedToken(operator: JKOperatorToken): JKOperatorToken? =
        when (operator) {
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

    private fun JKBinaryExpression.canBeSimplified(): Boolean {
        val operatorToken = operator.token
        if (operatorToken != JKOperatorToken.LT && operatorToken != JKOperatorToken.LTEQ
            && operatorToken != JKOperatorToken.GT && operatorToken != JKOperatorToken.GTEQ
        ) return true

        val leftType = left.calculateType(typeFactory)?.asPrimitiveType()
        val rightType = right.calculateType(typeFactory)?.asPrimitiveType()
        return leftType?.isFloatingPoint() == false && rightType?.isFloatingPoint() == false
    }
}