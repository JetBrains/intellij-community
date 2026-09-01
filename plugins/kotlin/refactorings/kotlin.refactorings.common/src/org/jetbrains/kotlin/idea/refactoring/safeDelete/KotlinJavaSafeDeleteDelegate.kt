// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.safeDelete

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.isAncestor
import com.intellij.refactoring.safeDelete.JavaSafeDeleteDelegate
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteCustomUsageInfo
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceSimpleDeleteUsageInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.javaInterop.callableSymbol
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulCall
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.refactoring.parentLabeledExpression
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstructorCalleeExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSuperTypeEntry
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex

class KotlinJavaSafeDeleteDelegate : JavaSafeDeleteDelegate {
    override fun createUsageInfoForParameter(
      reference: PsiReference,
      usages: MutableList<in UsageInfo>,
      parameter: PsiNamedElement,
      paramIdx: Int,
      isVararg: Boolean
    ) {
        val element = reference.element as? KtElement ?: return

        val originalParameter = parameter.unwrapped ?: return

        val parameterIndex = originalParameter.parameterIndex()
        if (parameterIndex < 0) return

        val target = reference.resolve() ?: return

        if (!PsiTreeUtil.isAncestor(target, originalParameter, true)) {
            return
        }

        val callExpression = element.getNonStrictParentOfType<KtCallElement>() ?: return

        val calleeExpression = callExpression.calleeExpression
        val isReferenceOrConstructorCalleeExpression = calleeExpression is KtReferenceExpression || calleeExpression is KtConstructorCalleeExpression
        if (!(isReferenceOrConstructorCalleeExpression && calleeExpression.isAncestor(element))) return

        analyze(callExpression) {
            createParameterUsagesFromResolvedCall(callExpression, usages, parameter, parameterIndex)
        }
    }

    @OptIn(KaExperimentalApi::class)
    context(session: KaSession)
    private fun createParameterUsagesFromResolvedCall(
        callExpression: KtCallElement,
        usages: MutableList<in UsageInfo>,
        parameter: PsiNamedElement,
        parameterIndex: Int,
    ) {
        val unwrapped = parameter.unwrapped
        val targetParameter = when (unwrapped) {
            is KtParameter -> unwrapped.symbol
            is PsiParameter -> {
                val method = unwrapped.declarationScope as? PsiMethod
                (method?.callableSymbol as? KaFunctionSymbol)?.valueParameters?.getOrNull(parameterIndex)
            }
            else -> null
        } ?: return

        val resolvedCall = callExpression.resolveSuccessfulCall() ?: return

        val valueArguments = callExpression.valueArguments.filterIsInstance<KtValueArgument>().filter { argument ->
            val argumentExpression = argument.getArgumentExpression()
            argumentExpression != null && resolvedCall.valueArgumentMapping[argumentExpression]?.symbol == targetParameter
        }

        if (valueArguments.isNotEmpty()) {
            usages.add(SafeDeleteValueArgumentListUsageInfo(parameter, *valueArguments.toTypedArray()))
            return
        }

        val lambdaArgument = callExpression.lambdaArguments.firstOrNull() ?: return
        val argumentExpression = lambdaArgument.getArgumentExpression() ?: return
        val mappedParameter = resolvedCall.valueArgumentMapping[argumentExpression]
            ?: argumentExpression.parentLabeledExpression()?.let(resolvedCall.valueArgumentMapping::get)

        if (mappedParameter?.symbol == targetParameter) {
            usages.add(SafeDeleteReferenceSimpleDeleteUsageInfo(lambdaArgument, parameter, true))
        }
    }

    override fun createJavaTypeParameterUsageInfo(
        reference: PsiReference,
        usages: MutableList<in UsageInfo>,
        typeParameter: PsiElement,
        paramsCount: Int,
        index: Int
    ) {
        val referencedElement = reference.element

        val argList = referencedElement.getNonStrictParentOfType<KtUserType>()?.typeArgumentList
            ?: referencedElement.getNonStrictParentOfType<KtCallExpression>()?.typeArgumentList

        if (argList != null) {
            val projections = argList.arguments
            if (index < projections.size) {
                usages.add(SafeDeleteTypeArgumentUsageInfo(projections[index], referencedElement))
            }
        }
    }

    override fun createCleanupOverriding(
      overriddenFunction: PsiElement,
      elements2Delete: Array<PsiElement>,
      result: MutableList<in UsageInfo>
    ) {
        result.add(object : SafeDeleteReferenceSimpleDeleteUsageInfo(overriddenFunction, overriddenFunction, true), SafeDeleteCustomUsageInfo {
            override fun performRefactoring() {
                (element as? KtModifierListOwner)?.modifierList?.getModifier(KtTokens.OVERRIDE_KEYWORD)?.delete()
            }
        })
    }

    override fun createExtendsListUsageInfo(refElement: PsiElement, reference: PsiReference): UsageInfo? {
        val element = reference.element
        return element.getParentOfTypeAndBranch<KtSuperTypeEntry> { typeReference }?.let {
            if (refElement is PsiClass && refElement.isInterface) {
                return SafeDeleteSuperTypeUsageInfo(it, refElement)
            }
            return null
        }
    }
}
