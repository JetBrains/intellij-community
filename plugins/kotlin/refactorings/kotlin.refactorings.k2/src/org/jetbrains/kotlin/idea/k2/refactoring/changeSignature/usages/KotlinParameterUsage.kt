// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages

import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfoBase
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinParameterInfo
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier

internal class KotlinParameterUsage(
    element: KtElement,
    private val parameterInfo: KotlinParameterInfo
) : UsageInfo(element), KotlinBaseUsage {
    override fun processUsage(
        changeInfo: KotlinChangeInfoBase,
        element: KtElement,
        allUsages: Array<out UsageInfo>
    ): KtElement {
        val newElement = KtPsiFactory(element.project).createExpression(getReplacementText(changeInfo))
        val elementToReplace = (element.parent as? KtThisExpression) ?: element
        return elementToReplace.replace(newElement) as KtElement
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
) : UsageInfo(element), KotlinBaseUsage {
    override fun processUsage(
        changeInfo: KotlinChangeInfoBase,
        element: KtElement,
        allUsages: Array<out UsageInfo>
    ): KtElement {
        val newElement = KtPsiFactory(element.project).createExpression(getReplacementText())
        val elementToReplace = (element.parent as? KtThisExpression) ?: element
        return elementToReplace.replace(newElement) as KtElement
    }

    private fun getReplacementText(): String = "this@${targetDescriptor.asString()}"
}

internal class KotlinImplicitThisToParameterUsage(
    callElement: KtElement,
    val parameterInfo: KotlinParameterInfo,
) : UsageInfo(callElement), KotlinBaseUsage {
    private fun getNewReceiverText(): String = parameterInfo.getInheritedName(null)
    override fun processUsage(
        changeInfo: KotlinChangeInfoBase,
        element: KtElement,
        allUsages: Array<out UsageInfo>
    ): KtElement {
        val newQualifiedCall = KtPsiFactory(element.project).createExpression("${getNewReceiverText()}.${element.text}") as KtQualifiedExpression
        return element.replace(newQualifiedCall) as KtElement
    }
}

internal class KotlinImplicitThisUsage(
    callElement: KtElement,
    private val targetDescriptor: Name
) : UsageInfo(callElement), KotlinBaseUsage {
    private fun getNewReceiverText() = when {
        targetDescriptor.isSpecial -> "this"
        else -> "this@${targetDescriptor.asString()}"
    }

    override fun processUsage(
        changeInfo: KotlinChangeInfoBase,
        element: KtElement,
        allUsages: Array<out UsageInfo>
    ): KtElement {
        val newQualifiedCall = KtPsiFactory(element.project).createExpression("${getNewReceiverText()}.${element.text}"
        ) as KtQualifiedExpression
        return element.replace(newQualifiedCall) as KtElement
    }
}