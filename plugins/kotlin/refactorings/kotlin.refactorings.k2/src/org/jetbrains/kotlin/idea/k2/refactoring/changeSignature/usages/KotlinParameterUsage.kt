// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages

import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfoBase
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinParameterInfo
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier

internal class KotlinParameterUsage(
    element: KtElement,
    private val parameterInfo: KotlinParameterInfo
) : UsageInfo(element), KotlinBaseChangeSignatureUsage {
    override fun processUsage(
        changeInfo: KotlinChangeInfoBase,
        element: KtElement,
        allUsages: Array<out UsageInfo>
    ): KtElement {
        val newElement = KtPsiFactory(element.project).createExpression(getReplacementText(changeInfo))
        val elementToReplace = (element.parent as? KtThisExpression) ?: element
        return elementToReplace.replace(newElement).parent as KtElement
    }

    private fun getReplacementText(changeInfo: KotlinChangeInfoBase): String {
        if (changeInfo.receiverParameterInfo != parameterInfo) return parameterInfo.getInheritedName(null)

        val newName = changeInfo.newName
        if (newName.isIdentifier()) return "this@$newName"

        return "this"
    }
}

internal class KotlinNonQualifiedOuterThisUsage(
    element: KtThisExpression,
    private val targetDescriptor: Name
) : UsageInfo(element), KotlinBaseChangeSignatureUsage {
    override fun processUsage(
        changeInfo: KotlinChangeInfoBase,
        element: KtElement,
        allUsages: Array<out UsageInfo>
    ): KtElement {
        val newElement = KtPsiFactory(element.project).createExpression(getReplacementText())
        val elementToReplace = (element.parent as? KtThisExpression) ?: element
        return elementToReplace.replace(newElement).parent as KtElement
    }

    private fun getReplacementText(): String = "this@${targetDescriptor.asString()}"
}

internal class KotlinImplicitThisToParameterUsage(
    callElement: KtElement,
    val parameterInfo: KotlinParameterInfo,
) : UsageInfo(callElement), KotlinBaseChangeSignatureUsage {
    private fun getNewReceiverText(): String = parameterInfo.getInheritedName(null)
    override fun processUsage(
        changeInfo: KotlinChangeInfoBase,
        element: KtElement,
        allUsages: Array<out UsageInfo>
    ): KtElement {
        val parent = element.parent
        if (parent is KtCallExpression) {
            return processUsage(changeInfo, parent, allUsages)
        }
        val newQualifiedCall = KtPsiFactory(element.project).createExpression("${getNewReceiverText()}.${element.text}") as KtQualifiedExpression
        return element.replace(newQualifiedCall) as KtElement
    }
}

internal class KotlinImplicitThisUsage(
    callElement: KtElement,
    private val newReceiver: String
) : UsageInfo(callElement), KotlinBaseChangeSignatureUsage {

    override fun processUsage(
        changeInfo: KotlinChangeInfoBase,
        element: KtElement,
        allUsages: Array<out UsageInfo>
    ): KtElement {
        val newQualifiedCall = KtPsiFactory(element.project).createExpression("$newReceiver.${element.text}"
        ) as KtQualifiedExpression
        return element.replace(newQualifiedCall).parent as KtElement
    }
}