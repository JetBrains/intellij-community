// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.j2k.CodeBuilder

@K1Deprecation
fun CodeBuilder.appendWithPrefix(element: Element, prefix: String): CodeBuilder = if (!element.isEmpty) this append prefix append element else this
@K1Deprecation
fun CodeBuilder.appendWithSuffix(element: Element, suffix: String): CodeBuilder = if (!element.isEmpty) this append element append suffix else this

@K1Deprecation
fun CodeBuilder.appendOperand(expression: Expression, operand: Expression, parenthesisForSamePrecedence: Boolean = false): CodeBuilder {
    val parentPrecedence = expression.precedence() ?: throw IllegalArgumentException("Unknown precedence for $expression")
    val operandPrecedence = operand.precedence()
    val needParenthesis = operandPrecedence != null &&
            (parentPrecedence < operandPrecedence || parentPrecedence == operandPrecedence && parenthesisForSamePrecedence)
    if (needParenthesis) append("(")
    append(operand)
    if (needParenthesis) append(")")
    return this
}

@K1Deprecation
fun Element.wrapToBlockIfRequired(): Element = when (this) {
    is AssignmentExpression -> if (isMultiAssignment()) Block.of(this).assignNoPrototype() else this
    else -> this
}


private fun Expression.precedence(): Int? {
    return when (this) {
        is QualifiedExpression, is MethodCallExpression, is ArrayAccessExpression, is PostfixExpression, is BangBangExpression, is StarExpression -> 0

        is PrefixExpression -> 1

        is TypeCastExpression -> 2

        is BinaryExpression -> op.precedence

        is RangeExpression, is UntilExpression, is DownToExpression -> 5

        is IsOperator -> 8

        is IfStatement -> 13

        is AssignmentExpression -> 14

        else -> null
    }
}
