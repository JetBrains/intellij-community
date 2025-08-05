// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.callOn
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.primitiveTypes
import org.jetbrains.kotlin.resolve.jvm.JvmPrimitiveType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.*

/**
 * Converts operations on boxed types to more idiomatic Kotlin expressions:
 *   1. Unwrap constructor calls: `new Double(10.1)` -> `10.1`
 *   2. Change type conversion calls: `double.longValue()` -> `double.toLong()`
 */
class BoxedTypeOperationsConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        return recurse(
            when (element) {
                is JKQualifiedExpression -> convertBoxedTypeUnwrapping(element)
                is JKNewExpression -> convertCreationOfBoxedType(element)
                else -> null
            } ?: element
        )
    }

    private fun convertCreationOfBoxedType(newExpression: JKNewExpression): JKExpression? {
        if (newExpression.classSymbol.fqName !in boxedTypeFqNames) return null
        val singleArgument = newExpression.arguments.arguments.singleOrNull() ?: return null
        return singleArgument::value.detached()
    }

    private fun convertBoxedTypeUnwrapping(qualifiedExpression: JKQualifiedExpression): JKExpression? {
        val methodCallExpression = qualifiedExpression.selector.safeAs<JKCallExpression>() ?: return null
        val (boxedJavaType, operationType) =
            primitiveTypeUnwrapRegexp.matchEntire(methodCallExpression.identifier.fqName)
                ?.groupValues
                ?.let {
                    it[1] to it[2]
                } ?: return null
        val primitiveTypeName = boxedTypeToPrimitiveType[boxedJavaType] ?: return null
        if (operationType !in primitiveTypeNames) return null

        val receiver = qualifiedExpression::receiver.detached()
        if (primitiveTypeName == operationType) {
            // This is a call like `integer.intValue()`, useless from Kotlin's point of view.
            // Just return the receiver itself.
            return receiver.withFormattingFrom(qualifiedExpression)
        }

        val typeName = primitiveTypeName.replaceFirstChar { it.titlecase(Locale.US) }
        val conversionTypeName = operationType.replaceFirstChar { it.titlecase(Locale.US) }
        val shouldConvertToIntFirst =
            primitiveTypeName in floatingPointPrimitiveTypeNames && operationType in typeNameOfIntegersLesserThanInt

        val replacement = if (shouldConvertToIntFirst) {
            receiver.callOn(symbolProvider.provideMethodSymbol("kotlin.$typeName.toInt"))
                .callOn(symbolProvider.provideMethodSymbol("kotlin.Int.to$conversionTypeName"))
        } else {
            receiver.callOn(symbolProvider.provideMethodSymbol("kotlin.$typeName.to$conversionTypeName"))
        }

        return replacement.withFormattingFrom(qualifiedExpression)
    }
}

private val boxedTypeFqNames: List<String> =
    primitiveTypes.map { it.wrapperFqName.asString() }

private val boxedTypeToPrimitiveType: Map<String, String> =
    primitiveTypes.associate { it.wrapperFqName.asString() to it.javaKeywordName }

private val primitiveTypeNames: List<String> =
    primitiveTypes.map { it.javaKeywordName }

private val primitiveTypeUnwrapRegexp: Regex =
    """([\w.]+)\.(\w+)Value""".toRegex()

private val floatingPointPrimitiveTypeNames: List<String> =
    listOf(JvmPrimitiveType.DOUBLE.javaKeywordName, JvmPrimitiveType.FLOAT.javaKeywordName)

private val typeNameOfIntegersLesserThanInt: List<String> =
    listOf(JvmPrimitiveType.SHORT.javaKeywordName, JvmPrimitiveType.BYTE.javaKeywordName)
