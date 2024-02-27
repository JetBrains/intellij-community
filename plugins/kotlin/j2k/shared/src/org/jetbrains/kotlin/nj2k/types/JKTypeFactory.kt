// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.types

import com.intellij.psi.*
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.KtStarTypeProjection
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.j2k.Nullability
import org.jetbrains.kotlin.j2k.Nullability.NotNull
import org.jetbrains.kotlin.j2k.Nullability.Nullable
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.nj2k.JKSymbolProvider
import org.jetbrains.kotlin.nj2k.symbols.JKClassSymbol
import org.jetbrains.kotlin.nj2k.symbols.JKTypeParameterSymbol
import org.jetbrains.kotlin.nj2k.symbols.JKUnresolvedClassSymbol
import org.jetbrains.kotlin.resolve.jvm.JvmPrimitiveType

class JKTypeFactory(val symbolProvider: JKSymbolProvider) {
    fun fromPsiType(type: PsiType): JKType = createPsiType(type)

    context(KtAnalysisSession)
    fun fromKtType(type: KtType): JKType = createKtType(type)

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

        val boolean = typeByFqName(StandardNames.FqNames._boolean)
        val char = typeByFqName(StandardNames.FqNames._char)
        val byte = typeByFqName(StandardNames.FqNames._byte)
        val short = typeByFqName(StandardNames.FqNames._short)
        val int = typeByFqName(StandardNames.FqNames._int)
        val float = typeByFqName(StandardNames.FqNames._float)
        val long = typeByFqName(StandardNames.FqNames._long)
        val double = typeByFqName(StandardNames.FqNames._double)

        val string = typeByFqName(StandardNames.FqNames.string)

        val unit = typeByFqName(StandardNames.FqNames.unit)
        val nothing = typeByFqName(StandardNames.FqNames.nothing)
        val nullableAny = typeByFqName(StandardNames.FqNames.any, nullability = Nullable)
    }

    fun fromPrimitiveType(primitiveType: JKJavaPrimitiveType) = when (primitiveType.jvmPrimitiveType) {
        JvmPrimitiveType.BOOLEAN -> types.boolean
        JvmPrimitiveType.CHAR -> types.char
        JvmPrimitiveType.BYTE -> types.byte
        JvmPrimitiveType.SHORT -> types.short
        JvmPrimitiveType.INT -> types.int
        JvmPrimitiveType.FLOAT -> types.float
        JvmPrimitiveType.LONG -> types.long
        JvmPrimitiveType.DOUBLE -> types.double
    }

    val types by lazy(LazyThreadSafetyMode.NONE) { DefaultTypes() }

    private fun createPsiType(type: PsiType): JKType = when (type) {
        is PsiClassType -> {
            val target = type.resolve()
            val parameters = type.parameters.map { fromPsiType(it) }
            when (target) {
                null ->
                    JKClassType(JKUnresolvedClassSymbol(type.rawType().canonicalText, this), parameters)

                is PsiTypeParameter ->
                    JKTypeParameterType(symbolProvider.provideDirectSymbol(target) as JKTypeParameterSymbol)

                is PsiAnonymousClass -> {
                    /*
                     If anonymous class is declared inside the converting code, we will not be able to access JKUniverseClassSymbol's target
                     And get UninitializedPropertyAccessException exception, so it is ok to use base class for now
                    */
                    createPsiType(target.baseClassType)
                }

                else -> {
                    JKClassType(
                        target.let { symbolProvider.provideDirectSymbol(it) as JKClassSymbol },
                        parameters
                    )
                }
            }
        }

        is PsiArrayType -> JKJavaArrayType(fromPsiType(type.componentType))
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

        is PsiLambdaExpressionType -> type.expression.functionalInterfaceType?.let(::createPsiType) ?: JKNoType
        is PsiMethodReferenceType -> type.expression.functionalInterfaceType?.let(::createPsiType) ?: JKNoType
        else -> JKNoType
    }

    context(KtAnalysisSession)
    private fun createKtType(type: KtType): JKType {
        return when (type) {
            is KtTypeParameterType -> {
                val symbol = symbolProvider.provideDirectSymbol(type.symbol) as? JKTypeParameterSymbol ?: return JKNoType
                JKTypeParameterType(symbol)
            }

            is KtNonErrorClassType -> {
                val fqName = type.classId.asSingleFqName()
                val classReference = symbolProvider.provideClassSymbol(fqName)
                val typeParameters = type.ownTypeArguments.map { typeArgument ->
                    if (typeArgument is KtStarTypeProjection) {
                        JKStarProjectionTypeImpl
                    } else {
                        val typeArgumentType = typeArgument.type ?: return JKNoType
                        createKtType(typeArgumentType)
                    }
                }
                val nullability = if (type.nullability.isNullable) Nullable else NotNull
                JKClassType(classReference, typeParameters, nullability)
            }

            else -> JKNoType
        }
    }
}
