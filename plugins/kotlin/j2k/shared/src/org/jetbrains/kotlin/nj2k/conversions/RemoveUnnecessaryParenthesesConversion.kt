// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.lang.BinaryOperationPrecedence
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.psi.KtPsiUtil

/**
 * Analogous to `RemoveUnnecessaryParenthesesIntention`.
 * Removes parentheses (conservatively) added by other steps.
 */
class RemoveUnnecessaryParenthesesConversion(context: ConverterContext) : RecursiveConversion(context) {

    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKParenthesizedExpression) return recurse(element)
        if (areParenthesesNecessary(element)) return recurse(element)
        return recurse(element::expression.detached().withFormattingFrom(element))
    }

    private fun areParenthesesNecessary(expression: JKParenthesizedExpression): Boolean {
        val innerExpression = expression.expression
        val parent = expression.parent

        // We can end up with silly "preexisting" parentheses when converting expressions that take significantly different forms in Java
        // vs. Kotlin. For example, we can have unnecessary parentheses wrapping the left side of an assignment when turning an expression
        // like `a = b += (c) = ((d *= e))` into `(c) = ((e.let { d *= it; d })) ...`
        if ((parent is JKKtAssignmentStatement && parent.field == expression) ||
            (parent is JKAssignmentChainLetLink && parent.receiver == expression)
        ) {
            return false
        }

        // If parentheses were present in the original Java code, leave them be
        if (expression.shouldBePreserved) return true

        if (parent == null) return true

        // Nested parentheses are always unnecessary
        if (innerExpression is JKParenthesizedExpression || parent is JKParenthesizedExpression) return false

        // Conditions in if-else/while/do-while expressions have their own parentheses (e.g., the `(isEnabled)` in `if (isEnabled) return`)
        // Also, arguments don't need parentheses.
        if ((parent is JKIfElseExpression && parent.condition === expression) ||
            (parent is JKIfElseStatement && parent.condition === expression) ||
            (parent is JKWhileStatement && parent.condition === expression) ||
            (parent is JKDoWhileStatement && parent.condition === expression) ||
            parent is JKArgument
        ) {
            return false
        }

        // We can omit parentheses for a binary expression like `1 + \n 2` but not one like `5 + 3 \n -2`
        if (innerExpression is JKBinaryExpression && innerExpression.recursivelyContainsNewlineBeforeOperator()) {
            return true
        }

        // Some parent nodes always surround their child with unambiguous whitespace/keywords/parentheses/commands/etc. and have only one
        // child node that can possibly be surrounded by parentheses
        if (parent is JKDelegationConstructorCall ||
            parent is JKKtWhenCase ||
            parent is JKReturnStatement
        ) {
            return false
        }

        // Keep parentheses on the receiver in expressions like `(str1 + str2).length`
        if ((parent is JKQualifiedExpression && parent.receiver == expression) ||
            (parent is JKAssignmentChainAlsoLink && parent.receiver == expression)
        ) {
            return !innerExpression.isAtomic()
        }

        if (parent is JKForInStatement && parent.iterationExpression == expression) return false

        if (parent is JKIsExpression && parent.expression == expression) return false

        // No need for parentheses if the expression is a standalone statement living in a block
        val grandparent = parent.parent
        if (parent is JKExpressionStatement && (grandparent is JKLambdaExpression || grandparent is JKBlockImpl)) {
            return false
        }

        // if parenthesized expression is the initializer, e.g. `val x = (doThing())`
        if ((parent is JKLocalVariable && parent.initializer == expression) ||
            (parent is JKField && parent.initializer == expression) ||
            (parent is JKKtAssignmentStatement && parent.expression == expression)
        ) {
            return false
        }

        if (parent is JKPrefixExpression || parent is JKPostfixExpression) {
            return !innerExpression.isAtomic()
        }

        // remove parentheses if doing so doesn't affect the order of operations
        val innerPriority = getPriority(innerExpression)
        val parentPriority = getPriority(parent)
        if (innerPriority == parentPriority) {
            if (parent !is JKBinaryExpression) return false
            if (innerExpression is JKBinaryExpression &&
                (innerExpression.operator.token == JKOperatorToken.ANDAND ||
                        innerExpression.operator.token == JKOperatorToken.OROR)
            ) {
                return false
            }
            return parent.right == expression
        }

        return innerPriority < parentPriority
    }

    /**
     * Based on `org.jetbrains.kotlin.psi.KtPsiUtil#getPriority`
     */
    private fun getPriority(expression: JKElement): Int {
        if (expression is JKSuperExpression) {
            return KtPsiUtil.MAX_PRIORITY
        }

        if (expression is JKPostfixExpression ||
            expression is JKQualifiedExpression ||
            expression is JKCallExpression ||
            expression is JKMethodReferenceExpression ||
            expression is JKArrayAccessExpression
        ) {
            return KtPsiUtil.MAX_PRIORITY - 1
        }

        if (expression is JKPrefixExpression || expression is JKLabeledExpression || expression is JKIfElseExpression) {
            return KtPsiUtil.MAX_PRIORITY - 2
        }

        if (expression is JKBinaryExpression) {
            val operatorToken = expression.operator.token
            val operatorElementType = JKOperatorToken.toKtElementType(operatorToken)

            val binaryOperation = operatorElementType ?: if (operatorToken is JKKtWordOperatorToken) {
                // all infix functions (e.g. `shl`, `until`) are JKKtWordOperatorTokens
                KtTokens.IDENTIFIER
            } else {
                null
            }

            val binaryOperationPrecedence = BinaryOperationPrecedence.TOKEN_TO_BINARY_PRECEDENCE_MAP[binaryOperation];
            if (binaryOperationPrecedence != null) {
                return (KtPsiUtil.MAX_PRIORITY - 3) - binaryOperationPrecedence.ordinal;
            }
        }

        return KtPsiUtil.MAX_PRIORITY
    }
}