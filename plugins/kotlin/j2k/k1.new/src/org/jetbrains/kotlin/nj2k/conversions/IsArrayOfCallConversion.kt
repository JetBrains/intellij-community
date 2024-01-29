// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.j2k.Nullability
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveApplicableConversionBase
import org.jetbrains.kotlin.nj2k.identifier
import org.jetbrains.kotlin.nj2k.symbols.JKFieldSymbol
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.*
import org.jetbrains.kotlin.nj2k.unboxFieldReference

internal class IsArrayOfCallConversion(override val context: NewJ2kConverterContext) :
    RecursiveApplicableConversionBase(context) {

    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKIsExpression) return recurse(element)
        if (!element.type.type.isArrayType()) return recurse(element)
        val operand = element.expression
        val operandType = operand.calculateType(typeFactory) ?: return recurse(element)
        val type = element::type.detached().type

        val isArrayOfExpression = JKQualifiedExpression(
            operand.detached(element), JKCallExpressionImpl(
                symbolProvider.provideMethodSymbol("kotlin.isArrayOf"),
                JKArgumentList(), JKTypeArgumentList(type.arrayInnerType()!!)
            )
        )
        val replacement = if (operandType.isArrayType()) {
            isArrayOfExpression
        } else {
            JKBinaryExpression(
                JKIsExpression(
                    JKFieldAccessExpression(operand.identifier!! as JKFieldSymbol),
                    JKClassType(
                        symbolProvider.provideClassSymbol("kotlin.Array"),
                        listOf(JKStarProjectionTypeImpl),
                        Nullability.NotNull
                    ).asTypeElement()
                ),
                isArrayOfExpression,
                JKKtOperatorImpl(JKOperatorToken.ANDAND, typeFactory.types.boolean)
            )
        }
        return recurse(replacement)
    }
}