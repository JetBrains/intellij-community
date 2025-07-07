// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiSuperMethodUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.changeSignature.ChangeSignatureUsageProvider
import com.intellij.refactoring.changeSignature.JavaChangeInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.impl.base.references.KaBaseSimpleNameReference
import org.jetbrains.kotlin.analysis.api.resolution.KaSymbolBasedReference
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.*
import org.jetbrains.kotlin.idea.references.KtArrayAccessReference
import org.jetbrains.kotlin.idea.references.KtInvokeFunctionReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
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
        val kotlinChangeInfoBase = fromJavaChangeInfo(changeInfo, UsageInfo(unwrapped), true)
        if (kotlinChangeInfoBase != null) {
            KotlinChangeSignatureUsageSearcher.findInternalUsages(unwrapped, kotlinChangeInfoBase, result)
        }
        return KotlinOverrideUsageInfo(unwrapped, baseMethod, isCaller(changeInfo, unwrapped))
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
                    callElementParent != null -> {
                        val isCopyOfDataClass = analyze(element) {
                            val functionSymbol = (reference as? KtSimpleNameReference)?.resolveToSymbol() as? KaNamedFunctionSymbol
                            functionSymbol?.origin == KaSymbolOrigin.SOURCE_MEMBER_GENERATED &&
                                    functionSymbol.name.asString() == "copy" && (functionSymbol.containingSymbol as? KaNamedClassSymbol)?.isData == true
                        }
                        if (isCopyOfDataClass) return null
                        isCaller(changeInfo, reference.resolve())
                        val callers = (changeInfo as? JavaChangeInfo)?.methodsToPropagateParameters
                        if (callers != null && callers.isNotEmpty()) {
                            val targets = reference.resolve()?.toLightMethods()
                            if (targets != null && targets.any { child -> callers.any { child == it || PsiSuperMethodUtil.isSuperMethod(child, it) } } ) {
                                return KotlinCallerCallUsage(callElementParent)
                            }
                        }
                        return KotlinFunctionCallUsage(callElementParent, method)
                    }
                    parent is KtConstructorDelegationCall ->
                        return KotlinConstructorDelegationCallUsage(parent, method)
                    element is KtSimpleNameExpression && (method is KtProperty || method is KtParameter) ->
                        return KotlinPropertyCallUsage(element, changeInfo as KotlinChangeInfoBase)
                    element is KtSuperTypeCallEntry ->
                        return KotlinFunctionCallUsage(element, method)
                    parent is KtCallableReferenceExpression ->
                        return KotlinCallableReferenceUsage(parent)
                    else -> {
                        //skip imports for now, they are removed by optimize imports
                        if (PsiTreeUtil.getParentOfType(element, KtImportDirective::class.java, true, KtDeclaration::class.java) != null) {
                            return null
                        }

                        if (PsiTreeUtil.getParentOfType(element, KtTypeReference::class.java, true, KtDeclaration::class.java) != null) {
                            return null
                        }
                    }
                }
                LOG.debug("Unsupported element: ${element.javaClass}")
            }
        }
        return null
    }

    private fun isCaller(changeInfo: ChangeInfo, element: PsiElement?): Boolean {
        val callers = (changeInfo as? JavaChangeInfo)?.methodsToPropagateParameters
        if (callers != null && callers.isNotEmpty()) {
            val targets = element?.toLightMethods()
            return targets != null && targets.any { child -> callers.any { child == it || PsiSuperMethodUtil.isSuperMethod(child, it) } }
        }
        return false
    }
}