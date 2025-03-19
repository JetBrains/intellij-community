// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages

import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.RemoveEmptyParenthesesFromLambdaCallUtils.removeEmptyArgumentListIfApplicable
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.OperatorToFunctionConverter
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.ReplaceGetOrSetInspectionUtils
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfoBase
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.util.OperatorNameConventions

internal class KotlinByConventionCallUsage(
    expression: KtExpression,
    private val callee: PsiElement
) : UsageInfo(expression), KotlinBaseChangeSignatureUsage {
    private var convertedCallExpression: KtCallExpression? = null

    private fun foldExpression(expression: KtDotQualifiedExpression, changeInfo: ChangeInfo) {
        when (changeInfo.newName) {
            OperatorNameConventions.INVOKE.asString() -> {
                OperatorToFunctionConverter.replaceExplicitInvokeCallWithImplicit(expression)
                    ?.getPossiblyQualifiedCallExpression()
                    ?.valueArgumentList?.let(::removeEmptyArgumentListIfApplicable)
            }

            OperatorNameConventions.GET.asString() -> {
                if (ReplaceGetOrSetInspectionUtils.looksLikeGetOrSetOperatorCall(expression)) {
                    ReplaceGetOrSetInspectionUtils.replaceGetOrSetWithPropertyAccessor(
                        expression,
                        isSet = false,
                        null
                    )
                }
            }
        }
    }

    override fun getElement(): PsiElement? {
        return convertedCallExpression ?: super.getElement()
    }

    private lateinit var callProcessor: KotlinFunctionCallUsage

    fun preprocessUsage() {
        val element = element as? KtExpression ?: return
        val convertedExpression = OperatorToFunctionConverter.convert(element).first
        val callExpression = convertedExpression.getPossiblyQualifiedCallExpression() ?: return
        convertedCallExpression = callExpression
        callProcessor = KotlinFunctionCallUsage(callExpression, callee)
    }

    override fun processUsage(
      changeInfo: KotlinChangeInfoBase,
      element: KtElement,
      allUsages: Array<out UsageInfo>
    ): KtElement? {
        val callExpression = convertedCallExpression ?: return null

        val newExpression = callProcessor.processUsageAndGetResult(
            changeInfo = changeInfo,
            element = callExpression,
            allUsages = allUsages,
            skipRedundantArgumentList = true,
        ) as? KtExpression

        val qualifiedCall = newExpression?.getQualifiedExpressionForSelectorOrThis() as? KtDotQualifiedExpression
        if (qualifiedCall != null) {
            foldExpression(qualifiedCall, changeInfo)
        }
        return null
    }
}