// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.changeSignature.JavaChangeSignatureUsageProcessor
import com.intellij.refactoring.changeSignature.MethodCallUsageInfo
import com.intellij.refactoring.changeSignature.OverriderUsageInfo
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.KotlinByConventionCallUsage
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.KotlinChangeSignatureConflictingUsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.KotlinFunctionCallUsage
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.KotlinOverrideUsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.KotlinPropertyCallUsage
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.idea.refactoring.conflicts.areSameSignatures
import org.jetbrains.kotlin.idea.refactoring.conflicts.checkNewPropertyConflicts
import org.jetbrains.kotlin.idea.refactoring.conflicts.checkRedeclarationConflicts
import org.jetbrains.kotlin.idea.refactoring.conflicts.registerAlreadyDeclaredConflict
import org.jetbrains.kotlin.idea.refactoring.conflicts.registerRetargetJobOnPotentialCandidates
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
        if (originalInfo.isNameChanged || originalInfo.isParameterSetOrOrderChanged || originalInfo.isParameterTypesChanged) {
            val unresolvableCollisions = mutableListOf<UsageInfo>()
            analyze(function) {
                registerRetargetJobOnPotentialCandidates(function, originalInfo.newName, {
                    filterCandidates(function, it)
                }) {
                    registerAlreadyDeclaredConflict(it, unresolvableCollisions)
                }
            }
            for (info in unresolvableCollisions) {
                when (info) {
                    is BasicUnresolvableCollisionUsageInfo -> {
                        result.putValue(info.element, info.description)
                    }
                }
            }
        }


        val parametersToRemove = originalInfo.parametersToRemove
        if (originalInfo.checkUsedParameters) {
            checkParametersToDelete(function, originalInfo)
        }

        for (parameter in originalInfo.getNonReceiverParameters()) {
            val parameterName = parameter.name
            if (parameter.oldName != parameterName || parameter.isNewParameter) {
                val unresolvableCollisions = mutableListOf<UsageInfo>()
                val ktParameter = when {
                    parameter.isNewParameter -> null
                    parameter.wasContextParameter -> function.modifierList?.contextReceiverList?.contextParameters()?.getOrNull(parameter.oldIndex)
                    else -> function.valueParameters[max(0, parameter.oldIndex - if (function.receiverTypeReference != null) 1 else 0)]
                }
                if (ktParameter != null) {
                    checkRedeclarationConflicts(ktParameter, parameterName, unresolvableCollisions)
                }
                else {
                    if (originalInfo.getNonReceiverParameters().any { it != parameter && it.name == parameterName }) {
                        result.putValue(function, KotlinBundle.message("text.duplicating.parameter", parameterName))
                    }
                }

                if (function is KtConstructor<*> && parameter.valOrVar != KotlinValVar.None && !(ktParameter != null && ktParameter.hasValOrVar())) {

                    val containingClass = function.containingClassOrObject
                    if (containingClass != null) {
                        checkNewPropertyConflicts(containingClass, parameterName, unresolvableCollisions)
                    }
                }
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
            if (function.receiverTypeReference == null && newReceiverInfo != null && newReceiverInfo.isNewParameter) {
                val usages = mutableListOf<UsageInfo>()
                KotlinChangeSignatureUsageSearcher.findReceiverReferences(function, usages, originalInfo)
                usages.filterIsInstance<KotlinChangeSignatureConflictingUsageInfo>().forEach { result.putValue(it.element, it.conflictMessage) }
            }
            findInternalExplicitReceiverConflicts(function, refUsages.get(), originalReceiverInfo)
            findReceiverToParameterInSafeCallsConflicts(refUsages.get())
        }


        val usageInfos = refUsages.get()
        val hasDefaultParameter = originalInfo.newParameters.any { it.defaultValueAsDefaultParameter || it.defaultValueForCall != null }
        for (usageInfo in usageInfos) {
            when (usageInfo) {
                is KotlinOverrideUsageInfo -> {
                    if (originalInfo.checkUsedParameters) {
                        checkParametersToDelete(usageInfo.element as KtCallableDeclaration, originalInfo)
                    }
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

    context(KaSession)
    private fun KtPsiFactory.createContextType(text: String, context: KtElement): KaType? {
        return createTypeCodeFragment(text, context).getContentElement()?.type
    }
    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun filterCandidates(function: KtCallableDeclaration, candidateSymbol: KaDeclarationSymbol): Boolean {
        val factory = KtPsiFactory(function.project)
        val newReceiverType = originalInfo.receiverParameterInfo?.currentType?.text?.let {
            factory.createContextType(it, function)
        }
        val newParameterTypes = originalInfo.getNonReceiverParameters().mapNotNull {
            it.currentType.text?.let {
                factory.createContextType(it, function)
            }
        }
        return candidateSymbol is KaFunctionSymbol &&
                areSameSignatures(
                    newReceiverType,
                    candidateSymbol.receiverType,
                    newParameterTypes,
                    candidateSymbol.valueParameters.map { it.returnType }, //todo currently context receiver can't be changed
                    (function.symbol as? KaFunctionSymbol)?.contextReceivers ?: emptyList(),
                    candidateSymbol.contextReceivers
                )
    }

    private fun checkParametersToDelete(
        callableDeclaration: KtCallableDeclaration,
        changeInfo: KotlinChangeInfo,
    ) {
        val toRemove = changeInfo.parametersToRemove
        val valueParameters = callableDeclaration.valueParameters
        val hasReceiver = callableDeclaration.receiverTypeReference != null
        if (hasReceiver && toRemove[0]) {
            val usages = mutableListOf<UsageInfo>()
            KotlinChangeSignatureUsageSearcher.findReceiverReferences(callableDeclaration, usages, originalInfo)
            if (usages.isNotEmpty()) {
                result.putValue(
                    callableDeclaration.receiverTypeReference,
                    KotlinBundle.message("parameter.used.in.declaration.body.warning", KotlinBundle.message("text.receiver")),
                )
            }
        }

        for ((i, parameter) in valueParameters.withIndex()) {
            val index = (if (hasReceiver) 1 else 0) + i
            if (toRemove[index]) {
                registerConflictIfUsed(parameter)
            }
        }

        val oldContextParameters = callableDeclaration.modifierList?.contextReceiverList?.contextParameters()
        if (oldContextParameters != null && oldContextParameters.isNotEmpty()) {
            val usedIndexes = changeInfo.newParameters.filter { it.wasContextParameter }.map { it.oldIndex }
            oldContextParameters.withIndex().filter { it.index !in usedIndexes }.forEach {
                registerConflictIfUsed(it.value)
                // t o d o search implicit usages
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

    private fun registerConflictIfUsed(
        element: PsiNamedElement
    ) {
        if (ReferencesSearch.search(element).filtering { ref ->
                val refElement = ref.element
                !(refElement is KtSimpleNameExpression && refElement.parent is KtValueArgumentName)
            }.filtering { ref ->
                KotlinChangeSignatureConflictFilter.EP_NAME.extensionList.none { it.skipUsage(element, ref) }
            }.findFirst() != null) {
            result.putValue(element, KotlinBundle.message("parameter.used.in.declaration.body.warning", element.name.toString()))
        }
    }
}