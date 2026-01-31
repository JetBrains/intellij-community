// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k

import com.intellij.codeInsight.Nullability
import com.intellij.codeInspection.dataFlow.CommonDataflow
import com.intellij.codeInspection.dataFlow.DfaNullability
import com.intellij.codeInspection.dataFlow.DfaUtil
import com.intellij.codeInspection.dataFlow.types.DfReferenceType
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.TypeConversionUtil
import com.siyeh.ig.psiutils.ExpectedTypeUtils
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.asJava.elements.KtLightDeclaration
import org.jetbrains.kotlin.psi.KtTypeParameter

/**
 * A collection of types with known nullability as determined by [J2KNullityInferrer].
 * This information is used to set the nullability of created `JKType`s in [org.jetbrains.kotlin.nj2k.types.JKTypeFactory].
 */
internal data class NullabilityInfo(
    val nullableTypes: Set<PsiType>,
    val notNullTypes: Set<PsiType>,
    val nullableElements: Set<PsiJavaCodeReferenceElement>,
    val notNullElements: Set<PsiJavaCodeReferenceElement>,
)

// Only Kotlin type parameters are supported
internal fun getTypeParameterNullability(psiTypeParameter: PsiTypeParameter): Nullability {
    val ktTypeParameter = (psiTypeParameter as? KtLightDeclaration<*, *>)?.kotlinOrigin as? KtTypeParameter
        ?: return Nullability.UNKNOWN

    analyze(ktTypeParameter) {
        for (upperBound in ktTypeParameter.symbol.upperBounds) {
            if (upperBound.nullability == KaTypeNullability.NON_NULLABLE) {
                return Nullability.NOT_NULL
            }
        }

        return Nullability.NULLABLE
    }
}

internal fun isUsedInAutoUnboxingContext(expr: PsiReferenceExpression): Boolean {
    val exprType = expr.type
    if (!TypeConversionUtil.isAssignableFromPrimitiveWrapper(exprType)) return false

    val expectedType = ExpectedTypeUtils.findExpectedType(expr, /* calculateTypeForComplexReferences = */ false) ?: return false
    if (!TypeConversionUtil.isPrimitiveAndNotNull(expectedType)) return false

    val unboxedType = PsiPrimitiveType.getUnboxedType(exprType) ?: return false
    return expectedType.isAssignableFrom(unboxedType)
}

// The result is cached by the Java DFA itself (this function is still hot, though)
internal fun getExpressionDfaNullability(expr: PsiExpression): DfaNullability? {
    val dataflowResult = CommonDataflow.getDataflowResult(expr) ?: return null
    val dfType = dataflowResult.getDfType(expr)
    if (dfType !is DfReferenceType) return null
    return dfType.getNullability()
}

// This function should not be called very often, but the computation is expensive, so it's better to cache it anyway
internal fun getMethodNullabilityByDfa(method: PsiMethod): Nullability {
    return CachedValuesManager.getManager(method.project).getCachedValue(method, METHOD_NULLABILITY_KEY, {
        CachedValueProvider.Result(DfaUtil.inferMethodNullability(method), PsiModificationTracker.MODIFICATION_COUNT)
    }, false)
}

private val METHOD_NULLABILITY_KEY = Key.create<CachedValue<Nullability>>("METHOD_NULLABILITY")
