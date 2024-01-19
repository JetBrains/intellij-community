// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveApplicableConversionBase
import org.jetbrains.kotlin.nj2k.tree.*

internal class ParenthesizeBitwiseOperationConversion(override val context: NewJ2kConverterContext) :
    RecursiveApplicableConversionBase(context) {

    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKBinaryExpression) return recurse(element)
        val parent = element.parent as? JKBinaryExpression ?: return recurse(element)
        val operator = element.operator.token
        val parentOperator = parent.operator.token
        if (operator == parentOperator) return recurse(element)
        if (operator !is JKKtWordOperatorToken && parentOperator !is JKKtWordOperatorToken) return recurse(element)
        return recurse(element.parenthesised())
    }

    private fun JKBinaryExpression.parenthesised() =
        JKParenthesizedExpression(
            JKBinaryExpression(this::left.detached(), this::right.detached(), operator).withFormattingFrom(this)
        )
}
