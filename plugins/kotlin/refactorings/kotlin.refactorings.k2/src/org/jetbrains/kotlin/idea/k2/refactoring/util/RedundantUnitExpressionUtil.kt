// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.util

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.computeMissingCases
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.types.KaDynamicType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
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
            val expandedClassSymbol = expectedReturnType.expandedSymbol
            return expandedClassSymbol != null &&
                    !expectedReturnType.isMarkedNullable &&
                    expandedClassSymbol.classId != StandardClassIds.Any
        }
    }

    if (parent is KtBlockExpression) {
        if (referenceExpression == parent.lastBlockStatementOrThis()) {
            val parentIfOrWhen = PsiTreeUtil.getParentOfType(parent, true, KtIfExpression::class.java, KtWhenExpression::class.java)
            val prev = referenceExpression.previousStatement() ?: return parentIfOrWhen == null
            if (prev.isUnitLiteral()) return true
            if (prev is KtDeclaration && isDynamicCall(parent)) return false
            analyze(prev) {
                val ktType = prev.expressionType
                if (ktType != null) {
                    return ktType.isUnitType && !ktType.isMarkedNullable && prev.canBeUsedAsValue()
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


private fun isDynamicCall(parent: KtBlockExpression): Boolean = parent.getStrictParentOfType<KtFunctionLiteral>()?.findLambdaReturnType() is KaDynamicType

private fun KtReturnExpression.expectedReturnType(): KaType? = analyze(this) {
    targetSymbol?.let {
        (it.psi as? KtFunctionLiteral)?.findLambdaReturnType() ?: it.returnType
    }
}

private fun KtFunctionLiteral.findLambdaReturnType(): KaType? {
    val callExpression = getStrictParentOfType<KtCallExpression>() ?: return null
    val valueArgument = getStrictParentOfType<KtValueArgument>() ?: return null
    analyze(this) {
        val functionCallOrNull = callExpression.resolveToCall()?.singleFunctionCallOrNull() ?: return null
        val variableLikeSignature = functionCallOrNull.argumentMapping[valueArgument.getArgumentExpression()] ?: return null
        return (variableLikeSignature.returnType as? KaFunctionType)?.returnType
    }
}

context(_: KaSession)
private fun KtExpression.canBeUsedAsValue(): Boolean {
    return when (this) {
        is KtIfExpression -> {
            val elseExpression = `else`
            if (elseExpression is KtIfExpression) elseExpression.canBeUsedAsValue() else elseExpression != null
        }
        is KtWhenExpression ->
            entries.lastOrNull()?.elseKeyword != null || computeMissingCases().isEmpty()
        else ->
            true
    }
}