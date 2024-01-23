// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.types

import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiTypes
import org.jetbrains.kotlin.j2k.Nullability
import org.jetbrains.kotlin.nj2k.symbols.JKClassSymbol
import org.jetbrains.kotlin.nj2k.symbols.JKTypeParameterSymbol
import org.jetbrains.kotlin.resolve.jvm.JvmPrimitiveType

internal interface JKType {
    val nullability: Nullability
}

internal interface JKWildCardType : JKType

internal interface JKParametrizedType : JKType {
    val parameters: List<JKType>
}

internal interface JKStarProjectionType : JKWildCardType {
    override val nullability: Nullability
        get() = Nullability.NotNull
}

internal object JKNoType : JKType {
    override val nullability: Nullability = Nullability.NotNull
}

internal data class JKClassType(
    val classReference: JKClassSymbol,
    override val parameters: List<JKType> = emptyList(),
    override val nullability: Nullability = Nullability.Default
) : JKParametrizedType

internal object JKStarProjectionTypeImpl : JKStarProjectionType

internal object JKContextType : JKType {
    override val nullability: Nullability
        get() = Nullability.Default
}

internal data class JKVarianceTypeParameterType(
    val variance: Variance,
    val boundType: JKType
) : JKWildCardType {
    override val nullability: Nullability
        get() = Nullability.Default

    enum class Variance {
        IN, OUT
    }
}

internal data class JKTypeParameterType(
    val identifier: JKTypeParameterSymbol,
    override val nullability: Nullability = Nullability.Default
) : JKType

internal data class JKCapturedType(
    val wildcardType: JKWildCardType,
    override val nullability: Nullability = Nullability.Default
) : JKType

internal sealed class JKJavaPrimitiveTypeBase : JKType {
    abstract val jvmPrimitiveType: JvmPrimitiveType?
}

private data object JKJavaNullPrimitiveType : JKJavaPrimitiveTypeBase() {
    override val jvmPrimitiveType: JvmPrimitiveType? = null
    override val nullability: Nullability = Nullability.Nullable
}

internal class JKJavaPrimitiveType private constructor(override val jvmPrimitiveType: JvmPrimitiveType) : JKJavaPrimitiveTypeBase() {
    override val nullability: Nullability
        get() = Nullability.NotNull

    companion object {
        val BOOLEAN = JKJavaPrimitiveType(JvmPrimitiveType.BOOLEAN)
        val CHAR = JKJavaPrimitiveType(JvmPrimitiveType.CHAR)
        val BYTE = JKJavaPrimitiveType(JvmPrimitiveType.BYTE)
        val SHORT = JKJavaPrimitiveType(JvmPrimitiveType.SHORT)
        val INT = JKJavaPrimitiveType(JvmPrimitiveType.INT)
        val FLOAT = JKJavaPrimitiveType(JvmPrimitiveType.FLOAT)
        val LONG = JKJavaPrimitiveType(JvmPrimitiveType.LONG)
        val DOUBLE = JKJavaPrimitiveType(JvmPrimitiveType.DOUBLE)

        val ALL = listOf(BOOLEAN, CHAR, BYTE, SHORT, INT, FLOAT, LONG, DOUBLE)

        private val psiKindToJK =
            ALL.associateBy { JvmPrimitiveTypeKind.getKindByName(it.jvmPrimitiveType.javaKeywordName) }

        fun fromPsi(psi: PsiPrimitiveType) = when (psi) {
            PsiTypes.voidType() -> JKJavaVoidType
            PsiTypes.nullType() -> JKJavaNullPrimitiveType
            else -> psiKindToJK[psi.kind] ?: error("Invalid PSI type ${psi.presentableText}")
        }
    }

}

internal data class JKJavaArrayType(
    val type: JKType,
    override var nullability: Nullability = Nullability.Default
) : JKType

internal data class JKJavaDisjunctionType(
    val disjunctions: List<JKType>,
    override val nullability: Nullability = Nullability.Default
) : JKType

internal object JKJavaVoidType : JKType {
    override val nullability: Nullability
        get() = Nullability.NotNull
}
