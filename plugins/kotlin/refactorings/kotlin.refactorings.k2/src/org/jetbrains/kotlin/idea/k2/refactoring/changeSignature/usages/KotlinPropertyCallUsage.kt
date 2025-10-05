// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages

import com.intellij.psi.SmartPsiElementPointer
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allowAnalysisFromWriteActionInEdt
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfoBase
import org.jetbrains.kotlin.idea.k2.refactoring.util.createContextArgumentReplacementMapForVariableAccess
import org.jetbrains.kotlin.idea.k2.refactoring.util.createReplacementReceiverArgumentExpression
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis

internal class KotlinPropertyCallUsage(element: KtSimpleNameExpression, private val changeInfo: KotlinChangeInfoBase) : UsageInfo(element), KotlinBaseChangeSignatureUsage {
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
        updateName(element)
        return updateReceiver(element)
    }

    private fun updateName(element: KtSimpleNameExpression) {
        if (changeInfo.isNameChanged) {
            element.getReferencedNameElement().replace(KtPsiFactory(project).createExpression(changeInfo.newName))
        }
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

        return elementToReplace.replaced(replacingElement)
    }
}