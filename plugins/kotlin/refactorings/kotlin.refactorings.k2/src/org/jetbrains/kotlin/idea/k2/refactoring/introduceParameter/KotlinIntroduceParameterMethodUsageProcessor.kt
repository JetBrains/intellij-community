// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.refactoring.introduceParameter

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.changeSignature.JavaChangeInfoImpl
import com.intellij.refactoring.changeSignature.ParameterInfoImpl
import com.intellij.refactoring.introduceParameter.IntroduceParameterData
import com.intellij.refactoring.introduceParameter.IntroduceParameterMethodUsagesProcessor
import com.intellij.refactoring.util.CanonicalTypes
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.*
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.KotlinConstructorDelegationCallUsage
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.KotlinFunctionCallUsage
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.idea.searching.inheritors.findAllOverridings
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.types.Variance

class KotlinIntroduceParameterMethodUsageProcessor : IntroduceParameterMethodUsagesProcessor {
    override fun isMethodUsage(usage: UsageInfo): Boolean = (usage.element as? KtElement)?.let {
        it.getParentOfTypeAndBranch<KtCallElement>(true) { calleeExpression } != null
    } ?: false

    override fun findConflicts(data: IntroduceParameterData, usages: Array<UsageInfo>, conflicts: MultiMap<PsiElement, String>) {}

    @OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class, KaExperimentalApi::class) // called under potemkin progress
    override fun processChangeMethodSignature(data: IntroduceParameterData, usage: UsageInfo, usages: Array<out UsageInfo>): Boolean {
        val element = usage.element as? KtFunction ?: return true

        fun createChangeInfo(
            function: KtFunction, data: IntroduceParameterData
        ): KotlinChangeInfoBase {
            val methodDescriptor = KotlinMethodDescriptor(function)
            val changeInfo = KotlinChangeInfo(methodDescriptor)

            data.parameterListToRemove.sortedDescending().forEach { changeInfo.removeParameter(it) }

            val typeText = analyze(function) {
                data.forcedType.asKaType(function)?.render(position = Variance.INVARIANT)
            }

            //todo j2k
            val defaultValueForCall = (data.parameterInitializer.expression as? PsiExpression)?.text
            val originalType = KotlinTypeInfo(typeText, function)
            changeInfo.addParameter(
                KotlinParameterInfo(
                    originalIndex = -1,
                    originalType = originalType,
                    name = data.parameterName,
                    valOrVar = KotlinValVar.None,
                    defaultValueForCall = defaultValueForCall?.let {
                        KtPsiFactory.contextual(function).createExpressionIfPossible(defaultValueForCall)
                    },
                    defaultValueAsDefaultParameter = false,
                    defaultValue = null,
                    modifierList = null,
                    context = function
                )
            )
            return changeInfo
        }

        allowAnalysisFromWriteAction {
            allowAnalysisOnEdt {
                val changeInfo = createChangeInfo(element, data) // Java method is already updated at this point
                val kotlinFunctions = element.findAllOverridings().filterIsInstance<KtFunction>().toList()
                val changeSignatureUsageProcessor = KotlinChangeSignatureUsageProcessor()
                (kotlinFunctions + element).forEach {
                    changeSignatureUsageProcessor.updatePrimaryMethod(it, changeInfo, true)
                }
            }
        }

        return true
    }


    override fun processChangeMethodUsage(data: IntroduceParameterData, usage: UsageInfo, usages: Array<out UsageInfo>): Boolean {
        val refElement = usage.element as? KtReferenceExpression ?: return true

        val psiMethod = data.methodToReplaceIn
        val changeInfo = createChangeInfoFromJava(psiMethod, data, usage) ?: return true
        val callElement = refElement.getParentOfTypeAndBranch<KtCallElement>(true) { calleeExpression } ?: return true
        val delegateUsage = if (callElement is KtConstructorDelegationCall) {
            KotlinConstructorDelegationCallUsage(callElement, psiMethod)
        } else {
            KotlinFunctionCallUsage(callElement, psiMethod)
        }
        return delegateUsage.processUsage(changeInfo, callElement, usages) != null
    }

    private fun createChangeInfoFromJava(
        psiMethod: PsiMethod, data: IntroduceParameterData, usage: UsageInfo
    ): KotlinChangeInfoBase? {
        val params = mutableListOf<ParameterInfoImpl>()

        ParameterInfoImpl
            .fromMethod(psiMethod)
            .filterNot { data.parameterListToRemove.contains(it.oldParameterIndex) }
            .forEach(params::add)

        //todo run j2k
        params.add(
            ParameterInfoImpl.create(-1).withName(data.parameterName).withType(data.forcedType)
                .withDefaultValue(data.parameterInitializer.text)
        )

        val returnType = psiMethod.returnType
        val javaChangeInfo = JavaChangeInfoImpl.generateChangeInfo(
            /* method = */ psiMethod,
            /* generateDelegate = */ false,
            /* fixFieldConflicts = */ true,
            /* newVisibility = */ null,
            /* newName = */ psiMethod.name,
            /* newType = */ if (returnType != null) CanonicalTypes.createTypeWrapper(returnType) else null,
            /* parameterInfo = */ params.toTypedArray(),
            /* thrownExceptions = */ emptyArray(),
            /* propagateParametersMethods = */ emptySet(),
            /* propagateExceptionsMethods = */ emptySet()
        )
        return fromJavaChangeInfo(javaChangeInfo, usage)
    }

    override fun processAddSuperCall(data: IntroduceParameterData, usage: UsageInfo, usages: Array<out UsageInfo>): Boolean = true

    override fun processAddDefaultConstructor(data: IntroduceParameterData, usage: UsageInfo, usages: Array<out UsageInfo>): Boolean = true
}
