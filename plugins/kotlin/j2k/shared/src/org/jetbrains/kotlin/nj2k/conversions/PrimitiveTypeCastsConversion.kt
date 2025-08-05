// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.config.ApiVersion.Companion.KOTLIN_1_5
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.*
import org.jetbrains.kotlin.nj2k.types.JKJavaPrimitiveType.Companion.BOOLEAN
import org.jetbrains.kotlin.nj2k.types.JKJavaPrimitiveType.Companion.BYTE
import org.jetbrains.kotlin.nj2k.types.JKJavaPrimitiveType.Companion.CHAR
import org.jetbrains.kotlin.nj2k.types.JKJavaPrimitiveType.Companion.DOUBLE
import org.jetbrains.kotlin.nj2k.types.JKJavaPrimitiveType.Companion.FLOAT
import org.jetbrains.kotlin.nj2k.types.JKJavaPrimitiveType.Companion.INT
import org.jetbrains.kotlin.nj2k.types.JKJavaPrimitiveType.Companion.LONG
import org.jetbrains.kotlin.nj2k.types.JKJavaPrimitiveType.Companion.SHORT
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class PrimitiveTypeCastsConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        return recurse(convertTypeCastExpression(element) ?: element)
    }

    private fun convertTypeCastExpression(element: JKTreeElement): JKExpression? {
        if (element !is JKTypeCastExpression) return null

        return castToAsPrimitiveTypes(element.expression, element.type.type, strict = true)
    }

    companion object {
        fun RecursiveConversion.castToAsPrimitiveTypes(
            expression: JKExpression,
            toType: JKType,
            strict: Boolean
        ): JKExpression? {
            if (expression is JKPrefixExpression
                && (expression.operator.token == JKOperatorToken.PLUS || expression.operator.token == JKOperatorToken.MINUS)
            ) {
                val casted = castToAsPrimitiveTypes(expression.expression, toType, strict) ?: return null
                return JKPrefixExpression(casted, expression.operator).withFormattingFrom(expression)
            }

            if (expression is JKQualifiedExpression) {
                if (expression.selector.safeAs<JKCallExpression>()?.identifier?.fqName == "kotlin.Int.inv") {
                    val casted = castToAsPrimitiveTypes(expression.receiver, toType, strict) ?: return null
                    return JKQualifiedExpression(casted, expression::selector.detached()).withFormattingFrom(expression)
                }
            }

            val expressionTypeAsPrimitive = expression.calculateType(typeFactory)?.asPrimitiveType() ?: return null
            val toTypeAsPrimitive = toType.asPrimitiveType() ?: return null
            if (toTypeAsPrimitive == expressionTypeAsPrimitive) return null

            if (expression is JKLiteralExpression) {
                if (!strict &&
                    expressionTypeAsPrimitive == INT &&
                    (toTypeAsPrimitive == LONG || toTypeAsPrimitive == SHORT || toTypeAsPrimitive == BYTE)
                ) return null
                val expectedType = toTypeAsPrimitive.toLiteralType() ?: JKLiteralExpression.LiteralType.INT

                if (expressionTypeAsPrimitive.isNumberType()
                    && toTypeAsPrimitive.isNumberType()
                    && toTypeAsPrimitive.isStrongerThan(expressionTypeAsPrimitive)
                ) {
                    return JKLiteralExpression(
                        expression.literal,
                        expectedType
                    ).withFormattingFrom(expression)
                }
            }

            charConversion(expression, expressionTypeAsPrimitive, toTypeAsPrimitive)?.also { return it }

            val initialTypeName = expressionTypeAsPrimitive.kotlinName()
            val conversionFunctionName = "to${toTypeAsPrimitive.kotlinName()}"

            val receiver = expression.copyTreeAndDetach().parenthesizeIfCompoundExpression()
            val result = if ((expressionTypeAsPrimitive == FLOAT || expressionTypeAsPrimitive == DOUBLE) &&
                (toTypeAsPrimitive == BYTE || toTypeAsPrimitive == SHORT)
            ) {
                // conversions of floating point types to integral types lesser than Int is an error (KT-30360)
                // we have to convert in two steps
                receiver
                    .callOn(symbolProvider.provideMethodSymbol("kotlin.$initialTypeName.toInt"), expressionType = typeFactory.types.int)
                    .callOn(symbolProvider.provideMethodSymbol("kotlin.Int.$conversionFunctionName"), expressionType = toType)
            } else {
                JKQualifiedExpression(
                    receiver,
                    JKCallExpressionImpl(
                        symbolProvider.provideMethodSymbol("kotlin.$initialTypeName.$conversionFunctionName"),
                        JKArgumentList(),
                        expressionType = toType
                    ),
                    toType
                )
            }

            return result.withFormattingFrom(expression)
        }

        private fun RecursiveConversion.charConversion(
            expression: JKExpression,
            fromType: JKJavaPrimitiveType,
            toType: JKJavaPrimitiveType
        ): JKExpression? {
            if (fromType == toType || moduleApiVersion < KOTLIN_1_5) return null
            if (fromType == CHAR) {
                return charToPrimitiveConversion(expression, toType)?.withFormattingFrom(expression)
            }
            if (toType == CHAR) {
                return primitiveToCharConversion(expression, fromType)?.withFormattingFrom(expression)
            }
            return null
        }

        private fun RecursiveConversion.charToPrimitiveConversion(
            expression: JKExpression,
            toType: JKJavaPrimitiveType
        ): JKExpression? {
            if (toType == BOOLEAN) return null

            var result = expression.copyTreeAndDetach().parenthesizeIfCompoundExpression()

            result = JKQualifiedExpression(
                result,
                JKFieldAccessExpression(symbolProvider.provideFieldSymbol("kotlin.code"), typeFactory.types.int),
                typeFactory.types.int
            )
            if (toType != INT) {
                result = result.callOn(
                    symbolProvider.provideMethodSymbol("kotlin.Int.to${toType.kotlinName()}"),
                    expressionType = toType
                )
            }
            return result
        }

        private fun RecursiveConversion.primitiveToCharConversion(
            expression: JKExpression,
            fromType: JKJavaPrimitiveType
        ): JKExpression? {
            // Int.toChar() is not deprecated, leave it as is.
            if (fromType == BOOLEAN || fromType == INT) return null

            val result = expression.copyTreeAndDetach().parenthesizeIfCompoundExpression()

            if (fromType == FLOAT || fromType == DOUBLE) {
                return result.callOn(
                    symbolProvider.provideMethodSymbol("kotlin.${fromType.kotlinName()}.toInt"),
                    expressionType = typeFactory.types.int
                ).callOn(
                    symbolProvider.provideMethodSymbol("kotlin.Int.toChar"),
                    expressionType = typeFactory.types.char
                )
            }
            return JKCallExpressionImpl(
                symbolProvider.provideMethodSymbol("kotlin.Char"),
                JKArgumentList(
                    result.callOn(symbolProvider.provideMethodSymbol("kotlin.toUShort"), expressionType = typeFactory.types.short)
                ),
                expressionType = typeFactory.types.char
            )
        }
    }
}