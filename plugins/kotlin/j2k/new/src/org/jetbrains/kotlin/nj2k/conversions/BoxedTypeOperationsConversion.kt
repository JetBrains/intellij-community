// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveApplicableConversionBase
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.primitiveTypes
import org.jetbrains.kotlin.resolve.jvm.JvmPrimitiveType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.*

class BoxedTypeOperationsConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
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

        val shouldConvertToIntFirst =
            primitiveTypeName in floatingPointPrimitiveTypeNames && operationType in typeNameOfIntegersLesserThanInt

        val conversionType = if (shouldConvertToIntFirst) {
            "Int"
        } else {
            operationType.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        }

        val typeName = primitiveTypeName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        return JKQualifiedExpression(
            receiver,
            JKCallExpressionImpl(
                symbolProvider.provideMethodSymbol("kotlin.$typeName.to$conversionType"),
                JKArgumentList()
            )
        ).withFormattingFrom(qualifiedExpression)
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
