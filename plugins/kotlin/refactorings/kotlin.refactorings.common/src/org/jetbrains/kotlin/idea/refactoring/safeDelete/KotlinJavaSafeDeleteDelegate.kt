// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.safeDelete

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.isAncestor
import com.intellij.refactoring.safeDelete.JavaSafeDeleteDelegate
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteCustomUsageInfo
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceSimpleDeleteUsageInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
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

        val args = callExpression.valueArgumentList?.arguments ?: return

        val namedArguments = args.filter { arg -> arg is KtValueArgument && arg.getArgumentName()?.text == parameter.name }
        if (namedArguments.isNotEmpty()) {
            usages.add(SafeDeleteValueArgumentListUsageInfo(parameter, namedArguments.first()))
            return
        }

        val argCount = args.size
        if (parameterIndex < argCount) {
            if ((parameter as? KtParameter)?.isVarArg == true) {
                for (idx in paramIdx..argCount - 1) {
                    usages.add(SafeDeleteValueArgumentListUsageInfo(parameter, args[idx]))
                }
            } else {
                val argument = args[parameterIndex]
                if (argument.getArgumentName()?.text != null) {
                    //parameter name check already failed above
                    return
                }

                usages.add(SafeDeleteValueArgumentListUsageInfo(parameter, argument))
            }
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