// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.callOn
import org.jetbrains.kotlin.nj2k.parenthesizeIfBinaryExpression
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.*

class PrimitiveTypeCastsConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        return recurse(convertTypeCastExpression(element) ?: element)
    }

    private fun convertTypeCastExpression(element: JKTreeElement): JKExpression? {
        if (element !is JKTypeCastExpression) return null

        return castToAsPrimitiveTypes(element.expression, element.type.type, strict = true)
    }

    companion object {
        fun RecursiveApplicableConversionBase.castToAsPrimitiveTypes(
            expression: JKExpression,
            toType: JKType,
            strict: Boolean
        ): JKExpression? {
            if (expression is JKPrefixExpression
                && (expression.operator.token == JKOperatorToken.PLUS || expression.operator.token == JKOperatorToken.MINUS)
            ) {
                val casted = castToAsPrimitiveTypes(expression.expression, toType, strict) ?: return null
                return JKPrefixExpression(casted, expression.operator)
            }
            val expressionTypeAsPrimitive = expression.calculateType(typeFactory)?.asPrimitiveType() ?: return null
            val toTypeAsPrimitive = toType.asPrimitiveType() ?: return null
            if (toTypeAsPrimitive == expressionTypeAsPrimitive) return null

            if (expression is JKLiteralExpression) {
                if (!strict
                    && expressionTypeAsPrimitive == JKJavaPrimitiveType.INT
                    && (toTypeAsPrimitive == JKJavaPrimitiveType.LONG ||
                            toTypeAsPrimitive == JKJavaPrimitiveType.SHORT ||
                            toTypeAsPrimitive == JKJavaPrimitiveType.BYTE)
                ) return null
                val expectedType = toTypeAsPrimitive.toLiteralType() ?: JKLiteralExpression.LiteralType.INT

                if (expressionTypeAsPrimitive.isNumberType()
                    && toTypeAsPrimitive.isNumberType()
                    && toTypeAsPrimitive.isStrongerThan(expressionTypeAsPrimitive)) {
                    return JKLiteralExpression(
                        expression.literal,
                        expectedType
                    )
                }
            }

            charConversion(expression, expressionTypeAsPrimitive, toTypeAsPrimitive)?.also { return it }

            val initialTypeName = expressionTypeAsPrimitive.kotlinName()
            val conversionFunctionName = "to${toTypeAsPrimitive.kotlinName()}"
            return JKQualifiedExpression(
                expression.copyTreeAndDetach().parenthesizeIfBinaryExpression(),
                JKCallExpressionImpl(
                    symbolProvider.provideMethodSymbol("kotlin.$initialTypeName.$conversionFunctionName"),
                    JKArgumentList()
                )
            )
        }

        fun RecursiveApplicableConversionBase.charConversion(
            expression: JKExpression,
            fromType: JKJavaPrimitiveType,
            toType: JKJavaPrimitiveType
        ): JKExpression? {
            if (fromType == toType || moduleApiVersion < ApiVersion.KOTLIN_1_5) return null
            if (fromType == JKJavaPrimitiveType.CHAR) {
                return charToPrimitiveConversion(expression, toType)
            }
            if (toType == JKJavaPrimitiveType.CHAR) {
                return primitiveToCharConversion(expression, fromType)
            }
            return null
        }

        private fun RecursiveApplicableConversionBase.charToPrimitiveConversion(
            expression: JKExpression,
            toType: JKJavaPrimitiveType
        ): JKExpression? {
            if (toType == JKJavaPrimitiveType.BOOLEAN) return null

            var result = expression.copyTreeAndDetach().parenthesizeIfBinaryExpression()

            result = JKQualifiedExpression(
                result,
                JKFieldAccessExpression(symbolProvider.provideFieldSymbol("kotlin.code"))
            )
            if (toType != JKJavaPrimitiveType.INT) {
                result = result.callOn(
                    symbolProvider.provideMethodSymbol("kotlin.Int.to${toType.kotlinName()}")
                )
            }
            return result
        }

        private fun RecursiveApplicableConversionBase.primitiveToCharConversion(
            expression: JKExpression,
            fromType: JKJavaPrimitiveType
        ): JKExpression? {
            // Int.toChar() is not deprecated, leave it as is.
            if (fromType == JKJavaPrimitiveType.BOOLEAN || fromType == JKJavaPrimitiveType.INT) return null

            val result = expression.copyTreeAndDetach().parenthesizeIfBinaryExpression()

            if (fromType == JKJavaPrimitiveType.FLOAT || fromType == JKJavaPrimitiveType.DOUBLE) {
                return result.callOn(
                    symbolProvider.provideMethodSymbol("kotlin.${fromType.kotlinName()}.toInt")
                ).callOn(
                    symbolProvider.provideMethodSymbol("kotlin.Int.toChar")
                )
            }
            return JKCallExpressionImpl(
                symbolProvider.provideMethodSymbol("kotlin.Char"),
                JKArgumentList(
                    result.callOn(symbolProvider.provideMethodSymbol("kotlin.toUShort"))
                )
            )
        }
    }
}