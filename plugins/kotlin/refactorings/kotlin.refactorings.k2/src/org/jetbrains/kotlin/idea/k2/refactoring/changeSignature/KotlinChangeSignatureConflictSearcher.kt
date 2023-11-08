// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.changeSignature.JavaChangeSignatureUsageProcessor
import com.intellij.refactoring.changeSignature.MethodCallUsageInfo
import com.intellij.refactoring.changeSignature.OverriderUsageInfo
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.KotlinByConventionCallUsage
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.KotlinFunctionCallUsage
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.KotlinOverrideUsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.KotlinPropertyCallUsage
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinModifiableMethodDescriptor.Kind
import org.jetbrains.kotlin.idea.refactoring.conflicts.checkRedeclarationConflicts
import org.jetbrains.kotlin.idea.refactoring.rename.BasicUnresolvableCollisionUsageInfo
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import kotlin.math.max

class KotlinChangeSignatureConflictSearcher(
    private val originalInfo: KotlinChangeInfo,
    private val refUsages: Ref<Array<UsageInfo>>
) {
    private val result = MultiMap<PsiElement, String>()

    fun findConflicts(): MultiMap<PsiElement, String> {
        val function = originalInfo.method
        if (function !is KtCallableDeclaration) return result
        val kind = originalInfo.methodDescriptor.kind
        if (kind == Kind.FUNCTION && (originalInfo.isNameChanged || originalInfo.isParameterSetOrOrderChanged || originalInfo.isParameterTypesChanged)) {
            val unresolvableCollisions = mutableListOf<UsageInfo>()
            checkRedeclarationConflicts(function, originalInfo.newName, unresolvableCollisions)//todo changed type info
            for (info in unresolvableCollisions) {
                when (info) {
                    is BasicUnresolvableCollisionUsageInfo -> {
                        result.putValue(info.element, info.description)
                    }
                }
            }
        }


        val parametersToRemove = originalInfo.parametersToRemove
        checkParametersToDelete(function, parametersToRemove)

        for (parameter in originalInfo.getNonReceiverParameters()) {

            if (parameter.oldName != parameter.name && !parameter.isNewParameter) {//todo conflicts with new parameter
                val unresolvableCollisions = mutableListOf<UsageInfo>()
                checkRedeclarationConflicts(function.valueParameters[max(0, parameter.oldIndex - if (function.receiverTypeReference != null) 1 else 0)], parameter.name, unresolvableCollisions)
                for (info in unresolvableCollisions) {
                    when (info) {
                        is BasicUnresolvableCollisionUsageInfo -> {
                            result.putValue(info.element, info.description)
                        }
                    }
                }
            }
        }

        val newReceiverInfo = originalInfo.receiverParameterInfo
        val originalReceiverInfo = originalInfo.methodDescriptor.receiver
        if (newReceiverInfo != originalReceiverInfo) {
            //todo new receiver conflict
            findInternalExplicitReceiverConflicts(function, refUsages.get(), originalReceiverInfo)
            findReceiverToParameterInSafeCallsConflicts(refUsages.get())
        }


        val usageInfos = refUsages.get()
        val hasDefaultParameter = originalInfo.newParameters.any { it.defaultValueAsDefaultParameter || it.defaultValueForCall != null }
        for (usageInfo in usageInfos) {
            when (usageInfo) {
                is KotlinOverrideUsageInfo -> {
                    checkParametersToDelete(usageInfo.element as KtCallableDeclaration, parametersToRemove)
                }
                is OverriderUsageInfo -> {
                    JavaChangeSignatureUsageProcessor.ConflictSearcher.checkParametersToDelete(usageInfo.overridingMethod, parametersToRemove, result)
                }
                is MethodCallUsageInfo -> {
                    val conflictMessage = when {
                        hasDefaultParameter -> KotlinBundle.message("change.signature.conflict.text.kotlin.default.value.in.non.kotlin.files")
                        else -> continue
                    }

                    result.putValue(usageInfo.element, conflictMessage)
                }
            }
        }
        return result
    }

    private fun checkParametersToDelete(
        callableDeclaration: KtCallableDeclaration,
        toRemove: BooleanArray,
    ) {
        val scope = LocalSearchScope(callableDeclaration)
        val valueParameters = callableDeclaration.valueParameters
        val hasReceiver = callableDeclaration.receiverTypeReference != null
        if (hasReceiver && toRemove[0]) {
            findReceiverUsages(callableDeclaration)
        }

        for ((i, parameter) in valueParameters.withIndex()) {
            val index = (if (hasReceiver) 1 else 0) + i
            if (toRemove[index]) {
                registerConflictIfUsed(parameter, scope)
            }
        }
    }

    private fun findInternalExplicitReceiverConflicts(
        function: KtCallableDeclaration,
        usages: Array<UsageInfo>,
        originalReceiverInfo: KotlinParameterInfo?
    ) {
        if (originalReceiverInfo != null) return

        val isObjectFunction = function.containingClassOrObject is KtObjectDeclaration

        loop@ for (usageInfo in usages) {
            if (!(usageInfo is KotlinFunctionCallUsage || usageInfo is KotlinPropertyCallUsage || usageInfo is KotlinByConventionCallUsage)) continue

            val callElement = usageInfo.element as? KtElement ?: continue

            val parent = callElement.parent

            val elementToReport = when {
                usageInfo is KotlinByConventionCallUsage -> callElement
                parent is KtQualifiedExpression && parent.selectorExpression === callElement && !isObjectFunction -> parent
                else -> continue@loop
            }

            val message = KotlinBundle.message(
                "text.explicit.receiver.is.already.present.in.call.element.0",
                CommonRefactoringUtil.htmlEmphasize(elementToReport.text)
            )
            result.putValue(callElement, message)
        }
    }

    private fun findReceiverToParameterInSafeCallsConflicts(
        usages: Array<UsageInfo>
    ) {
        val originalReceiverInfo = originalInfo.methodDescriptor.receiver
        if (originalReceiverInfo == null || originalReceiverInfo !in originalInfo.getNonReceiverParameters()) return

        for (usageInfo in usages) {
            if (!(usageInfo is KotlinFunctionCallUsage || usageInfo is KotlinPropertyCallUsage)) continue

            val callElement = usageInfo.element as? KtElement ?: continue
            val qualifiedExpression = callElement.getQualifiedExpressionForSelector()
            if (qualifiedExpression is KtSafeQualifiedExpression) {
                result.putValue(
                    callElement,
                    KotlinBundle.message(
                        "text.receiver.can.t.be.safely.transformed.to.value.argument",
                        CommonRefactoringUtil.htmlEmphasize(qualifiedExpression.text)
                    )
                )
            }
        }
    }

    private fun findReceiverUsages(
        callableDeclaration: KtCallableDeclaration,
    ) {
        val usages = mutableListOf<UsageInfo>()
        KotlinChangeSignatureUsageSearcher.findReceiverReferences(callableDeclaration, usages, originalInfo)
        if (!usages.isEmpty()) {
            result.putValue(
                callableDeclaration.receiverTypeReference,
                KotlinBundle.message("parameter.used.in.declaration.body.warning", KotlinBundle.message("text.receiver")),
            )
        }
    }

    private fun registerConflictIfUsed(
        element: PsiNamedElement,
        scope: LocalSearchScope
    ) {
        if (ReferencesSearch.search(element, scope).findFirst() != null) {
            result.putValue(element, KotlinBundle.message("parameter.used.in.declaration.body.warning", element.name.toString()))
        }
    }
}
