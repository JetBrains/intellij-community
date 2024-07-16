// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k

import com.intellij.codeInspection.dataFlow.CommonDataflow
import com.intellij.codeInspection.dataFlow.DfaNullability
import com.intellij.codeInspection.dataFlow.types.DfReferenceType
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiType
import com.intellij.psi.util.TypeConversionUtil
import com.siyeh.ig.psiutils.ExpectedTypeUtils

/**
 * The set of possible declaration types is determined by [J2KNullityInferrer],
 * currently: PsiMethod, PsiLocalVariable, PsiParameter, PsiField.
 */
internal data class DeclarationNullabilityInfo(
    val nullableTypes: Set<PsiType>,
    val notNullTypes: Set<PsiType>,
)

internal fun isUsedInAutoUnboxingContext(expr: PsiReferenceExpression): Boolean {
    val exprType = expr.type
    if (!TypeConversionUtil.isAssignableFromPrimitiveWrapper(exprType)) return false

    val expectedType = ExpectedTypeUtils.findExpectedType(expr, /* calculateTypeForComplexReferences = */ false) ?: return false
    if (!TypeConversionUtil.isPrimitiveAndNotNull(expectedType)) return false

    val unboxedType = PsiPrimitiveType.getUnboxedType(exprType) ?: return false
    return expectedType.isAssignableFrom(unboxedType)
}

internal fun getDfaNullability(expr: PsiExpression): DfaNullability? {
    val dataflowResult = CommonDataflow.getDataflowResult(expr) ?: return null
    val dfType = dataflowResult.getDfType(expr)
    if (dfType !is DfReferenceType) return null
    return dfType.getNullability()
}