// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.Nullability.NotNull
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.toArgumentList
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.*
import org.jetbrains.kotlin.resolve.ArrayFqNames

class ArrayInitializerConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        val newElement = when (element) {
            is JKJavaNewArray -> {
                val primitiveArrayType = element.type.type as? JKJavaPrimitiveType
                val arrayConstructorName =
                    if (primitiveArrayType != null)
                        ArrayFqNames.PRIMITIVE_TYPE_TO_ARRAY[PrimitiveType.valueOf(primitiveArrayType.jvmPrimitiveType.name)]!!.asString()
                    else
                        ArrayFqNames.ARRAY_OF_FUNCTION.asString()
                val arguments = element.initializer.also { element.initializer = emptyList() }.toArgumentList()
                arguments.hasTrailingComma = element.hasTrailingComma
                val typeArguments =
                    if (primitiveArrayType == null) JKTypeArgumentList(element::type.detached())
                    else JKTypeArgumentList()

                JKCallExpressionImpl(
                    symbolProvider.provideMethodSymbol("kotlin.$arrayConstructorName"),
                    arguments,
                    typeArguments,
                    canMoveLambdaOutsideParentheses = true
                )
            }

            is JKJavaNewEmptyArray -> {
                buildArrayInitializer(
                    element.initializer.also { element.initializer = emptyList() }, element.type.type
                )
            }

            else -> return recurse(element)
        }

        return recurse(newElement.withFormattingFrom(element))
    }

    private fun buildArrayInitializer(dimensions: List<JKExpression>, type: JKType): JKExpression {
        if (dimensions.size == 1) {
            return if (type !is JKJavaPrimitiveType) {
                JKCallExpressionImpl(
                    symbolProvider.provideMethodSymbol("kotlin.arrayOfNulls"),
                    JKArgumentList(dimensions[0]),
                    JKTypeArgumentList(type.updateNullability(NotNull))
                )
            } else {
                JKNewExpression(
                    symbolProvider.provideClassSymbol(type.arrayFqName()),
                    JKArgumentList(dimensions[0]),
                    canMoveLambdaOutsideParentheses = true
                )
            }
        }
        if (dimensions[1] !is JKStubExpression) {
            val arrayType = dimensions.drop(1).fold(type) { currentType, _ ->
                JKJavaArrayType(currentType)
            }
            return JKNewExpression(
                symbolProvider.provideClassSymbol("kotlin.Array"),
                JKArgumentList(
                    dimensions[0],
                    JKLambdaExpression(JKExpressionStatement(buildArrayInitializer(dimensions.subList(1, dimensions.size), type)))
                ),
                JKTypeArgumentList(arrayType),
                canMoveLambdaOutsideParentheses = true
            )
        }
        var resultType = JKClassType(
            symbolProvider.provideClassSymbol(type.arrayFqName()),
            if (type is JKJavaPrimitiveType) emptyList() else listOf(type)
        )
        for (i in 0 until dimensions.size - 2) {
            resultType = JKClassType(
                symbolProvider.provideClassSymbol(StandardNames.FqNames.array.toSafe()),
                listOf(resultType)
            )
        }
        return JKCallExpressionImpl(
            symbolProvider.provideMethodSymbol("kotlin.arrayOfNulls"),
            JKArgumentList(dimensions[0]),
            JKTypeArgumentList(resultType.updateNullability(NotNull))
        )
    }
}
