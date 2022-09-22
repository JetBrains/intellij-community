// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.safeDelete

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.isAncestor
import com.intellij.refactoring.safeDelete.JavaSafeDeleteDelegate
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceSimpleDeleteUsageInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex

class KotlinJavaSafeDeleteDelegate : JavaSafeDeleteDelegate {
    override fun createUsageInfoForParameter(
        reference: PsiReference,
        usages: MutableList<UsageInfo>,
        parameter: PsiNamedElement,
        paramIdx: Int,
        isVararg: Boolean
    ) {
        if (reference !is KtReference) return

        val element = reference.element

        val originalParameter = parameter.unwrapped ?: return

        val parameterIndex = originalParameter.parameterIndex()
        if (parameterIndex < 0) return

        val target = reference.resolve() ?: return

        if (!PsiTreeUtil.isAncestor(target, originalParameter, true)) {
            return
        }

        val callExpression = element.getNonStrictParentOfType<KtCallExpression>() ?: return

        val calleeExpression = callExpression.calleeExpression
        if (!(calleeExpression is KtReferenceExpression && calleeExpression.isAncestor(element))) return

        val args = callExpression.valueArguments

        val namedArguments = args.filter { arg -> arg is KtValueArgument && arg.getArgumentName()?.text == parameter.name }
        if (namedArguments.isNotEmpty()) {
            usages.add(SafeDeleteValueArgumentListUsageInfo(parameter, namedArguments.first()))
            return
        }

        val argCount = args.size
        if (parameterIndex < argCount) {
            usages.add(SafeDeleteValueArgumentListUsageInfo(parameter, args[parameterIndex] as KtValueArgument))
        } else {
            val lambdaArgs = callExpression.lambdaArguments
            val lambdaIndex = parameterIndex - argCount
            if (lambdaIndex < lambdaArgs.size) {
                usages.add(SafeDeleteReferenceSimpleDeleteUsageInfo(lambdaArgs[lambdaIndex], parameter, true))
            }
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
}