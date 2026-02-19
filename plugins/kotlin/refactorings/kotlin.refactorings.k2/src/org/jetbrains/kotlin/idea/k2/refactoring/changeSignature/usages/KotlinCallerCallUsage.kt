// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages

import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfoBase
import org.jetbrains.kotlin.idea.refactoring.isInsideOfCallerBody
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiFactory

class KotlinCallerCallUsage(element: KtCallElement) : UsageInfo(element), KotlinBaseChangeSignatureUsage{
    override fun processUsage(
        changeInfo: KotlinChangeInfoBase,
        element: KtElement,
        allUsages: Array<out UsageInfo>
    ): KtElement? {
        val argumentList = (element as? KtCallElement)?.valueArgumentList ?: return null
        val psiFactory = KtPsiFactory(project)
        val isNamedCall = argumentList.arguments.any { it.isNamed() }
        changeInfo.newParameters
            .filter { it.isNewParameter }
            .forEach {
                val parameterName = it.name
                val argumentExpression = if (element.isInsideOfCallerBody(allUsages) { isCaller(allUsages) }) {
                    psiFactory.createExpression(parameterName)
                } else {
                    it.defaultValueForCall ?: psiFactory.createExpression("_")
                }

                val argument = psiFactory.createArgument(
                    expression = argumentExpression,
                    name = if (isNamedCall) Name.identifier(parameterName) else null
                )

                argumentList.addArgument(argument)
            }

        return argumentList
    }
}
