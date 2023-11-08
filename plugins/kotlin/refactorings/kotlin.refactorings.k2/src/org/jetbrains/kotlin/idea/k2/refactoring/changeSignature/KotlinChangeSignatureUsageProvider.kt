// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.changeSignature.ChangeSignatureUsageProvider
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.KotlinByConventionCallUsage
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.KotlinConstructorDelegationCallUsage
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.KotlinFunctionCallUsage
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.KotlinOverrideUsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.KotlinPropertyCallUsage
import org.jetbrains.kotlin.idea.references.KtArrayAccessReference
import org.jetbrains.kotlin.idea.references.KtInvokeFunctionReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.util.match

private val LOG = Logger.getInstance(KotlinChangeSignatureUsageProvider::class.java)

class KotlinChangeSignatureUsageProvider : ChangeSignatureUsageProvider {
    override fun createOverrideUsageInfo(
        changeInfo: ChangeInfo,
        overrider: PsiElement,
        baseMethod: PsiElement,
        isOriginalOverrider: Boolean,
        isToModifyArgs: Boolean,
        isToThrowExceptions: Boolean,
        result: MutableList<in UsageInfo>
    ): UsageInfo {
        val unwrapped = overrider.unwrapped
        require(unwrapped is KtCallableDeclaration)
        val kotlinChangeInfoBase = fromJavaChangeInfo(changeInfo, UsageInfo(unwrapped))
        if (kotlinChangeInfoBase != null) {
            KotlinChangeSignatureUsageSearcher.findInternalUsages(unwrapped, kotlinChangeInfoBase, result)
        }
        return KotlinOverrideUsageInfo(unwrapped, baseMethod)
    }

    override fun createUsageInfo(
        changeInfo: ChangeInfo,
        reference: PsiReference,
        method: PsiElement,
        modifyArgs: Boolean,
        modifyExceptions: Boolean
    ): UsageInfo? {

        val element = reference.element
        when {
            reference is KtInvokeFunctionReference || reference is KtArrayAccessReference -> {
                return KotlinByConventionCallUsage(element as KtExpression, method)
            }

            element is KtReferenceExpression -> {
                val parent = element.parent
                val callElementParent = parent as? KtCallExpression
                    ?: element.parents.match(
                        KtUserType::class,
                        KtTypeReference::class,
                        KtConstructorCalleeExpression::class,
                        last = KtCallElement::class
                    )
                when {
                    callElementParent != null ->
                        return KotlinFunctionCallUsage(callElementParent, method)
                    parent is KtConstructorDelegationCall ->
                        return KotlinConstructorDelegationCallUsage(parent, method)
                    element is KtSimpleNameExpression && (method is KtProperty || method is KtParameter) ->
                        return KotlinPropertyCallUsage(element, changeInfo as KotlinChangeInfoBase)
                    element is KtSuperTypeCallEntry ->
                        return KotlinFunctionCallUsage(element, method)
                    else ->
                        //skip imports for now, they are removed by optimize imports
                        if (PsiTreeUtil.getParentOfType(element, KtImportDirective::class.java, true, KtDeclaration::class.java) != null) {
                            return null
                        }
                }
                LOG.error("Unsupported element: ${element.javaClass}")
            }
        }
        return null
    }
}