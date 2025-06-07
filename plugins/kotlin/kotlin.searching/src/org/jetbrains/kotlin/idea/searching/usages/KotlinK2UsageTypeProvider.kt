// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.searching.usages

import com.intellij.psi.PsiPackage
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinUsageTypeProvider
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.*
import org.jetbrains.kotlin.idea.references.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.util.OperatorNameConventions

internal class KotlinK2UsageTypeProvider : KotlinUsageTypeProvider() {

    override fun getUsageTypeEnumByReference(refExpr: KtReferenceExpression): UsageTypeEnum? {
        val reference = refExpr.mainReference
        check(reference is KtSimpleReference<*>) { "Reference should be KtSimpleReference but not ${reference::class}" }

        fun getFunctionUsageType(functionSymbol: KaFunctionSymbol): UsageTypeEnum? {
            when (reference) {
                is KtArrayAccessReference ->
                    return when ((functionSymbol as KaNamedFunctionSymbol).name) {
                        OperatorNameConventions.GET -> IMPLICIT_GET
                        OperatorNameConventions.SET -> IMPLICIT_SET
                        else -> error("Expected get or set operator but resolved to unexpected symbol {functionSymbol.render()}")
                    }
                is KtInvokeFunctionReference -> return IMPLICIT_INVOKE
                is KtConstructorDelegationReference -> return null
            }

            return when {
                refExpr.getParentOfTypeAndBranch<KtSuperTypeListEntry> { typeReference } != null -> SUPER_TYPE

                functionSymbol is KaConstructorSymbol && refExpr.getParentOfTypeAndBranch<KtAnnotationEntry> { typeReference } != null -> ANNOTATION

                with(refExpr.getParentOfTypeAndBranch<KtCallExpression> { calleeExpression }) {
                    this?.calleeExpression is KtSimpleNameExpression
                } -> {
                    val callExpression = refExpr.getNonStrictParentOfType<KtCallExpression>()
                    val qualifiedCall = callExpression?.parent as? KtDotQualifiedExpression

                    if (qualifiedCall?.receiverExpression is KtSuperExpression && qualifiedCall.selectorExpression == callExpression)
                        SUPER_DELEGATION
                    else if (functionSymbol is KaConstructorSymbol)
                        CLASS_NEW_OPERATOR
                    else
                        FUNCTION_CALL
                }

                refExpr.getParentOfTypeAndBranch<KtBinaryExpression> { operationReference } != null || refExpr.getParentOfTypeAndBranch<KtUnaryExpression> { operationReference } != null || refExpr.getParentOfTypeAndBranch<KtWhenConditionInRange> { operationReference } != null -> FUNCTION_CALL

                else -> null
            }
        }

        return analyze(refExpr) {
            when (val targetElement = reference.resolveToSymbol()) {
                is KaClassifierSymbol ->
                    when (targetElement) {
                        is KaClassSymbol -> when (targetElement.classKind) {
                          KaClassKind.COMPANION_OBJECT -> COMPANION_OBJECT_ACCESS
                          KaClassKind.OBJECT -> getVariableUsageType(refExpr)
                          else -> getClassUsageType(refExpr)
                        }
                        else -> getClassUsageType(refExpr)
                    }
                is KaPackageSymbol ->
                    if (targetElement.psi is PsiPackage) getPackageUsageType(refExpr) else getClassUsageType(refExpr)

                is KaVariableSymbol -> getVariableUsageType(refExpr)
                is KaFunctionSymbol -> getFunctionUsageType(targetElement)
                else -> null
            }
        }
    }
}
