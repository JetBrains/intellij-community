// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages

import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.single
import org.jetbrains.kotlin.analysis.api.resolution.variable
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allowAnalysisFromWriteActionInEdt
import org.jetbrains.kotlin.idea.base.analysis.api.utils.unwrapSmartCasts
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfoBase
import org.jetbrains.kotlin.idea.k2.refactoring.util.collectContextParameterValues
import org.jetbrains.kotlin.idea.k2.refactoring.util.createContextArgumentReplacementMapForVariableAccess
import org.jetbrains.kotlin.idea.k2.refactoring.util.createReplacementReceiverArgumentExpression
import org.jetbrains.kotlin.idea.refactoring.updateSimpleName
import org.jetbrains.kotlin.idea.util.tryResolveExpressionCall
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.getAssignmentByLHS
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.types.expressions.OperatorConventions

internal class KotlinPropertyCallUsage(
    element: KtSimpleNameExpression, private val changeInfo: KotlinChangeInfoBase
) : UsageInfo(element), KotlinBaseChangeSignatureUsage, WithContextParameters {
    private val contextParameters: Map<Int, SmartPsiElementPointer<KtExpression>>? =
        allowAnalysisFromWriteActionInEdt(element) {
            createContextArgumentReplacementMapForVariableAccess(element)
        }

    override fun processUsage(
      changeInfo: KotlinChangeInfoBase,
      element: KtElement,
      allUsages: Array<out UsageInfo>
    ): KtElement? {
        if (element !is KtSimpleNameExpression) return null
        element.updateSimpleName(changeInfo)
        return updateReceiver(element)
    }

    private fun updateReceiver(element: KtSimpleNameExpression): KtExpression? {
        val newReceiver = changeInfo.receiverParameterInfo
        val oldReceiver = changeInfo.oldReceiverInfo
        if (newReceiver == oldReceiver) return null

        val elementToReplace = element.getQualifiedExpressionForSelectorOrThis()

        val replacingElement = newReceiver?.let {
            val psiFactory = KtPsiFactory(project)
            val receiver = createReplacementReceiverArgumentExpression(
                psiFactory = psiFactory,
                newReceiverInfo = newReceiver,
                argumentMapping = emptyMap(),
                contextParameters = contextParameters,
            )
            psiFactory.createExpressionByPattern("$0.$1", receiver, element)
        } ?: element

        val contextValues = if (newReceiver == null) getContextParametersValues(changeInfo) else emptyList()
        val newElement = elementToReplace.replaced(replacingElement)
        val wrappedElement = wrapIntoContextValues(newElement, contextValues, KtPsiFactory(element.project), ::expandToElementToWrap)
        return (wrappedElement ?: newElement) as? KtExpression
    }

    @OptIn(KaExperimentalApi::class)
    private val explicitReceiver: SmartPsiElementPointer<KtExpression>? =
        allowAnalysisFromWriteActionInEdt(element) {
            val variableAccessCall = element.tryResolveExpressionCall()?.single?.variable
            val receiverValue = variableAccessCall?.extensionReceiver?.unwrapSmartCasts()
            (receiverValue as? KaExplicitReceiverValue)?.expression?.createSmartPointer()
        }

    override fun getContextParametersValues(changeInfo: KotlinChangeInfoBase): List<String> =
        collectContextParameterValues(changeInfo) { parameter ->
            explicitReceiver?.takeIf { parameter == changeInfo.oldReceiverInfo }
        }

    private fun expandToElementToWrap(element: KtElement): KtElement {
        val accessExpression = (element.parent as? KtQualifiedExpression)?.takeIf { it.selectorExpression == element } ?: element
        if (accessExpression !is KtExpression) return accessExpression
        accessExpression.getAssignmentByLHS()?.let { return it }
        val parent = accessExpression.parent
        if (parent is KtUnaryExpression && parent.operationToken in OperatorConventions.INCREMENT_OPERATIONS) return parent
        return accessExpression
    }
}