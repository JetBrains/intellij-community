// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.tree.*

class ParenthesizeBitwiseOperationConversion(override val context: NewJ2kConverterContext) :
    RecursiveApplicableConversionBase(context) {

    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKBinaryExpression) return recurse(element)
        val parent = element.parent as? JKBinaryExpression ?: return recurse(element)
        val operator = element.operator.token
        val parentOperator = parent.operator.token
        if (operator == parentOperator) return recurse(element)
        if (operator !is JKKtWordOperatorToken && parentOperator !is JKKtWordOperatorToken) return recurse(element)
        return recurse(
            JKParenthesizedExpression(
                JKBinaryExpression(
                    element::left.detached(),
                    element::right.detached(),
                    element.operator
                ).withFormattingFrom(element)
            )
        )
    }
}