// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.types

import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaStarTypeProjection
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.j2k.Nullability
import org.jetbrains.kotlin.j2k.Nullability.*
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.nj2k.NullabilityInfo
import org.jetbrains.kotlin.nj2k.JKSymbolProvider
import org.jetbrains.kotlin.nj2k.symbols.JKClassSymbol
import org.jetbrains.kotlin.nj2k.symbols.JKTypeParameterSymbol
import org.jetbrains.kotlin.nj2k.symbols.JKUnresolvedClassSymbol
import org.jetbrains.kotlin.resolve.jvm.JvmPrimitiveType

class JKTypeFactory(val symbolProvider: JKSymbolProvider) {
    internal var nullabilityInfo: NullabilityInfo? = null

    fun fromPsiType(type: PsiType): JKType = createFromPsiType(type)

    context(_: KaSession)
    fun fromKaType(type: KaType): JKType = createFromKaType(type)

    inner class DefaultTypes {
        private fun typeByFqName(
            fqName: FqNameUnsafe,
            typeArguments: List<JKType> = emptyList(),
            nullability: Nullability = NotNull
        ) = JKClassType(
            symbolProvider.provideClassSymbol(fqName),
            typeArguments,
            nullability
        )

        val boolean: JKClassType = typeByFqName(StandardNames.FqNames._boolean)
        val char: JKClassType = typeByFqName(StandardNames.FqNames._char)
        val byte: JKClassType = typeByFqName(StandardNames.FqNames._byte)
        val short: JKClassType = typeByFqName(StandardNames.FqNames._short)
        val int: JKClassType = typeByFqName(StandardNames.FqNames._int)
        val float: JKClassType = typeByFqName(StandardNames.FqNames._float)
        val long: JKClassType = typeByFqName(StandardNames.FqNames._long)
        val double: JKClassType = typeByFqName(StandardNames.FqNames._double)

        val string: JKClassType = typeByFqName(StandardNames.FqNames.string)

        val unit: JKClassType = typeByFqName(StandardNames.FqNames.unit)
        val nothing: JKClassType = typeByFqName(StandardNames.FqNames.nothing)
        val nullableAny: JKClassType = typeByFqName(StandardNames.FqNames.any, nullability = Nullable)
    }

    fun fromPrimitiveType(primitiveType: JKJavaPrimitiveType): JKClassType = when (primitiveType.jvmPrimitiveType) {
        JvmPrimitiveType.BOOLEAN -> types.boolean
        JvmPrimitiveType.CHAR -> types.char
        JvmPrimitiveType.BYTE -> types.byte
        JvmPrimitiveType.SHORT -> types.short
        JvmPrimitiveType.INT -> types.int
        JvmPrimitiveType.FLOAT -> types.float
        JvmPrimitiveType.LONG -> types.long
        JvmPrimitiveType.DOUBLE -> types.double
    }

    val types: DefaultTypes by lazy(LazyThreadSafetyMode.NONE) { DefaultTypes() }

    private fun createFromPsiType(type: PsiType): JKType {
        val nullability = getNullability(type)

        return when (type) {
            is PsiClassType -> {
                val target = type.resolve()
                val parameters = type.parameters.map { fromPsiType(it) }

                when (target) {
                    null ->
                        JKClassType(JKUnresolvedClassSymbol(type.rawType().canonicalText, this), parameters, nullability)

                    is PsiTypeParameter ->
                        JKTypeParameterType(symbolProvider.provideDirectSymbol(target) as JKTypeParameterSymbol, nullability)

                    is PsiAnonymousClass -> {
                        // If an anonymous class is declared inside the converting code,
                        // we will not be able to access JKUniverseClassSymbol's target
                        // and will get UninitializedPropertyAccessException exception,
                        // so it is ok to use the base class for now.
                        createFromPsiType(target.baseClassType)
                    }

                    else -> {
                        JKClassType(
                            symbolProvider.provideDirectSymbol(target) as JKClassSymbol,
                            parameters,
                            nullability
                        )
                    }
                }
            }

            is PsiArrayType -> JKJavaArrayType(fromPsiType(type.componentType), nullability)

            is PsiPrimitiveType -> JKJavaPrimitiveType.fromPsi(type)

            is PsiDisjunctionType ->
                JKJavaDisjunctionType(type.disjunctions.map { fromPsiType(it) })

            is PsiWildcardType ->
                when {
                    type.isExtends ->
                        JKVarianceTypeParameterType(
                            JKVarianceTypeParameterType.Variance.OUT,
                            fromPsiType(type.extendsBound)
                        )

                    type.isSuper ->
                        JKVarianceTypeParameterType(
                            JKVarianceTypeParameterType.Variance.IN,
                            fromPsiType(type.superBound)
                        )

                    else -> JKStarProjectionTypeImpl
                }

            is PsiCapturedWildcardType ->
                JKCapturedType(fromPsiType(type.wildcard) as JKWildCardType)

            is PsiIntersectionType -> // TODO what to do with intersection types? old j2k just took the first conjunct
                fromPsiType(type.representative)

            is PsiLambdaParameterType -> // Probably, means that we have erroneous Java code
                JKNoType

            is PsiLambdaExpressionType -> type.expression.functionalInterfaceType?.let(::createFromPsiType) ?: JKNoType

            is PsiMethodReferenceType -> type.expression.functionalInterfaceType?.let(::createFromPsiType) ?: JKNoType

            else -> JKNoType
        }
    }

    private fun getNullability(type: PsiType): Nullability {
        val info = nullabilityInfo ?: return Default
        val referenceElement = if (type is PsiClassReferenceType) type.reference else null
        val nullability = when {
            info.nullableTypes.contains(type) || info.nullableElements.contains(referenceElement) -> Nullable
            info.notNullTypes.contains(type) || info.notNullElements.contains(referenceElement) -> NotNull
            else -> Default
        }
        return nullability
    }

    context(_: KaSession)
    private fun createFromKaType(type: KaType): JKType {
        return when (type) {
            is KaTypeParameterType -> {
                val symbol = symbolProvider.provideDirectSymbol(type.symbol) as? JKTypeParameterSymbol ?: return JKNoType
                JKTypeParameterType(symbol)
            }

            is KaClassType -> {
                val fqName = type.classId.asSingleFqName()
                val classReference = symbolProvider.provideClassSymbol(fqName)
                val typeParameters = type.typeArguments.map { typeArgument ->
                    if (typeArgument is KaStarTypeProjection) {
                        JKStarProjectionTypeImpl
                    } else {
                        val typeArgumentType = typeArgument.type ?: return JKNoType
                        createFromKaType(typeArgumentType)
                    }
                }
                val nullability = if (type.nullability.isNullable) Nullable else NotNull
                JKClassType(classReference, typeParameters, nullability)
            }

            else -> JKNoType
        }
    }
}
