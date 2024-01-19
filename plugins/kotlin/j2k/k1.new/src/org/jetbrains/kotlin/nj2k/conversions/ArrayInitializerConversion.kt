// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveApplicableConversionBase
import org.jetbrains.kotlin.nj2k.toArgumentList
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.*
import org.jetbrains.kotlin.resolve.ArrayFqNames

internal class ArrayInitializerConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        var newElement = element
        if (element is JKJavaNewArray) {
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

            newElement = JKCallExpressionImpl(
                symbolProvider.provideMethodSymbol("kotlin.$arrayConstructorName"),
                arguments,
                typeArguments
            )
        } else if (element is JKJavaNewEmptyArray) {
            newElement = buildArrayInitializer(
                element.initializer.also { element.initializer = emptyList() }, element.type.type
            )
        }

        return recurse(newElement.withFormattingFrom(element))
    }

    private fun buildArrayInitializer(dimensions: List<JKExpression>, type: JKType): JKExpression {
        if (dimensions.size == 1) {
            return if (type !is JKJavaPrimitiveType) {
                JKCallExpressionImpl(
                    symbolProvider.provideMethodSymbol("kotlin.arrayOfNulls"),
                    JKArgumentList(dimensions[0]),
                    JKTypeArgumentList(type)
                )
            } else {
                JKNewExpression(
                    symbolProvider.provideClassSymbol(type.arrayFqName()),
                    JKArgumentList(dimensions[0])
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
                JKTypeArgumentList(arrayType)
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
            JKTypeArgumentList(resultType)
        )
    }
}
