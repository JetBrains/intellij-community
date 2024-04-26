// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.util

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.types.KtDynamicType
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.psi.previousStatement
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis

private fun KtExpression.isUnitLiteral(): Boolean {
    val referenceName = (this as? KtNameReferenceExpression)?.getReferencedNameAsName() ?: return false
    return referenceName == StandardNames.FqNames.unit.shortName()
}

fun isRedundantUnit(referenceExpression: KtReferenceExpression): Boolean {
    if (!referenceExpression.isUnitLiteral()) return false
    val parent = referenceExpression.parent ?: return false
    if (parent is KtReturnExpression) {
        analyze(parent) {
            val expectedReturnType = parent.expectedReturnType() ?: return false
            val expandedClassSymbol = expectedReturnType.expandedClassSymbol
            return expandedClassSymbol != null &&
                    !expectedReturnType.isMarkedNullable &&
                    expandedClassSymbol.classIdIfNonLocal != StandardClassIds.Any
        }
    }

    if (parent is KtBlockExpression) {
        if (referenceExpression == parent.lastBlockStatementOrThis()) {
            val parentIfOrWhen = PsiTreeUtil.getParentOfType(parent, true, KtIfExpression::class.java, KtWhenExpression::class.java)
            val prev = referenceExpression.previousStatement() ?: return parentIfOrWhen == null
            if (prev.isUnitLiteral()) return true
            if (prev is KtDeclaration && isDynamicCall(parent)) return false
            analyze(prev) {
                val ktType = prev.getKtType()
                if (ktType != null) {
                    return ktType.isUnit && !ktType.isMarkedNullable && prev.canBeUsedAsValue()
                }
            }

            if (prev !is KtDeclaration) return false
            if (prev !is KtFunction) return true
            return parentIfOrWhen == null
        }

        return true
    }

    return false
}


private fun isDynamicCall(parent: KtBlockExpression): Boolean = parent.getStrictParentOfType<KtFunctionLiteral>()?.findLambdaReturnType() is KtDynamicType

private fun KtReturnExpression.expectedReturnType(): KtType? = analyze(this) {
    getReturnTargetSymbol()?.let {
        (it.psi as? KtFunctionLiteral)?.findLambdaReturnType() ?: it.returnType
    }
}

private fun KtFunctionLiteral.findLambdaReturnType(): KtType? {
    val callExpression = getStrictParentOfType<KtCallExpression>() ?: return null
    val valueArgument = getStrictParentOfType<KtValueArgument>() ?: return null
    analyze(this) {
        val functionCallOrNull = callExpression.resolveCall()?.singleFunctionCallOrNull() ?: return null
        val variableLikeSignature = functionCallOrNull.argumentMapping[valueArgument.getArgumentExpression()] ?: return null
        return (variableLikeSignature.returnType as? KtFunctionalType)?.returnType
    }
}

context(KtAnalysisSession)
private fun KtExpression.canBeUsedAsValue(): Boolean {
    return when (this) {
        is KtIfExpression -> {
            val elseExpression = `else`
            if (elseExpression is KtIfExpression) elseExpression.canBeUsedAsValue() else elseExpression != null
        }
        is KtWhenExpression ->
            entries.lastOrNull()?.elseKeyword != null || getMissingCases().isEmpty()
        else ->
            true
    }
}