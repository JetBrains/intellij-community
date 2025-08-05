// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.callOn
import org.jetbrains.kotlin.nj2k.parenthesizeIfCompoundExpression
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.isStringType

class AnyWithStringConcatenationConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKBinaryExpression) return recurse(element)
        if (element.operator.token == JKOperatorToken.PLUS
            && element.right.calculateType(typeFactory)?.isStringType() == true
            && element.left.calculateType(typeFactory)?.isStringType() == false
        ) {
            return recurse(
                JKBinaryExpression(
                    element::left.detached().parenthesizeIfCompoundExpression()
                        .callOn(symbolProvider.provideMethodSymbol("kotlin.Any.toString"), expressionType = typeFactory.types.string),
                    element::right.detached(),
                    element.operator,
                    typeFactory.types.string
                ).withFormattingFrom(element)
            )
        }
        return recurse(element)
    }
}