// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.codeInsight.Nullability
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.codeInspection.dataFlow.CommonDataflow
import com.intellij.codeInspection.dataFlow.DfaNullability
import com.intellij.codeInspection.dataFlow.types.DfReferenceType
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.isMarkedNullable
import org.jetbrains.kotlin.asJava.elements.KtLightDeclaration
import org.jetbrains.kotlin.psi.KtTypeParameter
import java.util.Collections
import java.util.IdentityHashMap

/**
 * A collection of types with known nullability as determined by [J2KDataflowNullability].
 * This information is used to set the nullability of created `JKType`s in [org.jetbrains.kotlin.j2k.types.JKTypeFactory].
 */
internal class NullabilityInfo {
    // Identity sets tell apart the equal type arguments of one type
    val nullableTypes: MutableSet<PsiType> = Collections.newSetFromMap(IdentityHashMap())
    val notNullTypes: MutableSet<PsiType> = Collections.newSetFromMap(IdentityHashMap())

    // A recreated type object keeps its reference element
    val nullableElements: MutableSet<PsiJavaCodeReferenceElement> = HashSet()
    val notNullElements: MutableSet<PsiJavaCodeReferenceElement> = HashSet()

    // The data flow facts of the engine, so that J2K does not analyze the same code again
    val expressionDfTypes: MutableMap<PsiExpression, DfType> = HashMap()

    // Kept by declaration, because the type of a lambda parameter can share its reference element with another declaration
    val nullableLambdaParameters: MutableSet<PsiParameter> = HashSet()
    val notNullLambdaParameters: MutableSet<PsiParameter> = HashSet()
}

internal fun NullabilityInfo?.dfaNullabilityOf(expression: PsiExpression): DfaNullability? =
    this?.expressionDfTypes.orEmpty().dfaNullability(expression)

/** Returns the nullability that the engine found for [expression], otherwise runs [getExpressionDfaNullability]. */
internal fun Map<PsiExpression, DfType>.dfaNullability(expression: PsiExpression): DfaNullability? {
    val type = this[expression] ?: return getExpressionDfaNullability(expression)
    return (type as? DfReferenceType)?.nullability
}

internal val NULLABLE_DFA_NULLABILITIES = setOf(DfaNullability.NULL, DfaNullability.NULLABLE)

private fun getJavaTypeParameterNullability(psiTypeParameter: PsiTypeParameter): Nullability {
    val manager = NullableNotNullManager.getInstance(psiTypeParameter.project) ?: return Nullability.UNKNOWN
    manager.findOwnNullabilityInfo(psiTypeParameter)?.let { return it.nullability }
    manager.findDefaultTypeUseNullability(psiTypeParameter)?.let { return it.nullability }
    return Nullability.UNKNOWN
}

internal fun getTypeParameterNullability(psiTypeParameter: PsiTypeParameter): Nullability {
    val ktTypeParameter = (psiTypeParameter as? KtLightDeclaration<*, *>)?.kotlinOrigin as? KtTypeParameter
        ?: return getJavaTypeParameterNullability(psiTypeParameter)

    analyze(ktTypeParameter) {
        for (upperBound in ktTypeParameter.symbol.upperBounds) {
            if (!upperBound.isMarkedNullable) {
                return Nullability.NOT_NULL
            }
        }

        return Nullability.NULLABLE
    }
}

// The result is cached by the Java DFA itself (this function is still hot, though)
internal fun getExpressionDfaNullability(expr: PsiExpression): DfaNullability? {
    val dataflowResult = CommonDataflow.getDataflowResult(expr) ?: return null
    val dfType = dataflowResult.getDfType(expr)
    if (dfType !is DfReferenceType) return null
    return dfType.getNullability()
}

