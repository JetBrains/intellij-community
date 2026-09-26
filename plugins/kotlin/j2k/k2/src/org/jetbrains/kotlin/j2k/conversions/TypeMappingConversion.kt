// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.conversions

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.Nullability
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.j2k.RecursiveConversion
import org.jetbrains.kotlin.j2k.symbols.JKClassSymbol
import org.jetbrains.kotlin.j2k.symbols.JKUniverseClassSymbol
import org.jetbrains.kotlin.j2k.tree.JKClass
import org.jetbrains.kotlin.j2k.tree.JKClassLiteralExpression
import org.jetbrains.kotlin.j2k.tree.JKInheritanceInfo
import org.jetbrains.kotlin.j2k.tree.JKIsExpression
import org.jetbrains.kotlin.j2k.tree.JKNewExpression
import org.jetbrains.kotlin.j2k.tree.JKTreeElement
import org.jetbrains.kotlin.j2k.tree.JKTypeArgumentList
import org.jetbrains.kotlin.j2k.tree.JKTypeElement
import org.jetbrains.kotlin.j2k.tree.detached
import org.jetbrains.kotlin.j2k.tree.withPsiAndFormattingFrom
import org.jetbrains.kotlin.j2k.types.JKCapturedType
import org.jetbrains.kotlin.j2k.types.JKClassType
import org.jetbrains.kotlin.j2k.types.JKJavaArrayType
import org.jetbrains.kotlin.j2k.types.JKJavaPrimitiveType
import org.jetbrains.kotlin.j2k.types.JKJavaVoidType
import org.jetbrains.kotlin.j2k.types.JKStarProjectionTypeImpl
import org.jetbrains.kotlin.j2k.types.JKType
import org.jetbrains.kotlin.j2k.types.JKVarianceTypeParameterType
import org.jetbrains.kotlin.j2k.types.JKWildCardType
import org.jetbrains.kotlin.j2k.types.arrayFqName
import org.jetbrains.kotlin.j2k.types.updateNullability
import org.jetbrains.kotlin.psi.KtClass

class TypeMappingConversion(
    context: ConverterContext,
    val filter: (typeElement: JKTypeElement) -> Boolean = { true }
) : RecursiveConversion(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        when (element) {
            is JKTypeElement -> {
                if (filter(element)) {
                    element.type = element.type.mapType(element)
                }
            }

            is JKNewExpression -> {
                val newClassSymbol = element.classSymbol.mapClassSymbol()
                return recurse(
                    JKNewExpression(
                        newClassSymbol,
                        element::arguments.detached(),
                        element::typeArgumentList.detached().fixTypeArguments(newClassSymbol),
                        element::classBody.detached(),
                        element.isAnonymousClass,
                        canMoveLambdaOutsideParentheses = element.canMoveLambdaOutsideParentheses
                    ).withPsiAndFormattingFrom(element)
                )
            }
        }
        return recurse(element)
    }

    private fun JKTypeArgumentList.fixTypeArguments(classSymbol: JKClassSymbol): JKTypeArgumentList {
        if (typeArguments.isNotEmpty()) {
            return JKTypeArgumentList(
                typeArguments.map { typeArgument ->
                    JKTypeElement(typeArgument.type.mapType(null), typeArgument::annotationList.detached())
                }
            )
        }
        return when (val typeParametersCount = classSymbol.expectedTypeParametersCount()) {
            0 -> this
            else -> JKTypeArgumentList(List(typeParametersCount) {
                JKTypeElement(typeFactory.types.nullableAny)
            })
        }
    }

    private fun JKType.fixRawType(typeElement: JKTypeElement?) =
        when (typeElement?.parent) {
            is JKClassLiteralExpression -> this
            is JKIsExpression ->
                addTypeParametersToRawProjectionType(JKStarProjectionTypeImpl)
                    .updateNullability(Nullability.NotNull)

            is JKInheritanceInfo ->
                addTypeParametersToRawProjectionType(typeFactory.types.nullableAny)

            else ->
                addTypeParametersToRawProjectionType(JKStarProjectionTypeImpl)
        }

    private fun JKType.mapType(typeElement: JKTypeElement?): JKType =
        when (this) {
            is JKJavaPrimitiveType -> mapPrimitiveType()
            is JKClassType -> mapClassType()
            is JKJavaVoidType -> typeFactory.types.unit

            is JKJavaArrayType ->
                JKClassType(
                    symbolProvider.provideClassSymbol(type.arrayFqName()),
                    if (type is JKJavaPrimitiveType) emptyList() else listOf(type.mapType(typeElement)),
                    nullability
                )

            is JKVarianceTypeParameterType ->
                JKVarianceTypeParameterType(
                    variance,
                    boundType.mapType(null)
                )

            is JKCapturedType -> {
                JKCapturedType(
                    wildcardType.mapType(null) as JKWildCardType,
                    nullability
                )
            }

            else -> this
        }.fixRawType(typeElement)

    private fun JKClassSymbol.mapClassSymbol(): JKClassSymbol {
        if (this is JKUniverseClassSymbol) return this
        val newFqName = kotlinCollectionClassName()
            ?: kotlinStandardType()
            ?: fqName
        return symbolProvider.provideClassSymbol(newFqName)
    }

    private fun JKClassType.mapClassType(): JKClassType =
        JKClassType(
            classReference.mapClassSymbol(),
            parameters.map { it.mapType(null) },
            nullability
        )

    private fun JKClassSymbol.kotlinCollectionClassName(): String? =
        toKotlinMutableTypesMap[fqName]

    private val toKotlinMutableTypesMap: Map<String, String> = mapOf(
        CommonClassNames.JAVA_UTIL_ITERATOR to StandardNames.FqNames.mutableIterator.asString(),
        CommonClassNames.JAVA_UTIL_LIST to StandardNames.FqNames.mutableList.asString(),
        CommonClassNames.JAVA_UTIL_COLLECTION to StandardNames.FqNames.mutableCollection.asString(),
        CommonClassNames.JAVA_UTIL_SET to StandardNames.FqNames.mutableSet.asString(),
        CommonClassNames.JAVA_UTIL_MAP to StandardNames.FqNames.mutableMap.asString(),
        CommonClassNames.JAVA_UTIL_MAP_ENTRY to StandardNames.FqNames.mutableMapEntry.asString(),
        java.util.ListIterator::class.java.canonicalName to StandardNames.FqNames.mutableListIterator.asString()
    )

    private fun JKClassSymbol.kotlinStandardType(): String? {
        if (isKtFunction(fqName)) return fqName
        return JavaToKotlinClassMap.mapJavaToKotlin(FqName(fqName))?.asString()
    }

    private fun JKJavaPrimitiveType.mapPrimitiveType(): JKClassType =
        typeFactory.fromPrimitiveType(this)

    private inline fun <reified T : JKType> T.addTypeParametersToRawProjectionType(typeParameter: JKType): T =
        if (this is JKClassType && parameters.isEmpty()) {
            val parametersCount = classReference.expectedTypeParametersCount()
            val typeParameters = List(parametersCount) { typeParameter }
            JKClassType(
                classReference,
                typeParameters,
                nullability
            ) as T
        } else this

    private fun JKClassSymbol.expectedTypeParametersCount(): Int =
        when (val resolvedClass = target) {
            is PsiClass -> resolvedClass.typeParameters.size
            is KtClass -> resolvedClass.typeParameters.size
            is JKClass -> resolvedClass.typeParameterList.typeParameters.size
            else -> 0
        }

    companion object {
        private val ktFunctionRegex = "kotlin\\.jvm\\.functions\\.Function\\d+".toRegex()
        private fun isKtFunction(fqName: String) = ktFunctionRegex.matches(fqName)
    }
}