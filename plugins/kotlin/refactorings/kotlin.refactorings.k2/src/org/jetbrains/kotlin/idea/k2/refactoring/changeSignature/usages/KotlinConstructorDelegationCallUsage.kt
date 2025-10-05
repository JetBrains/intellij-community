// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages

import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfoBase
import org.jetbrains.kotlin.psi.KtConstructorDelegationCall
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSecondaryConstructor

internal class KotlinConstructorDelegationCallUsage(
    call: KtConstructorDelegationCall,
    method: PsiElement
) : UsageInfo(call), KotlinBaseChangeSignatureUsage {
    val delegate = KotlinFunctionCallUsage(call, method)
    override fun processUsage(
        changeInfo: KotlinChangeInfoBase,
        element: KtElement,
        allUsages: Array<out UsageInfo>
    ): KtElement? {
        if (element !is KtConstructorDelegationCall) return null
        val isThisCall = element.isCallToThis

        var elementToWorkWith = element
        if (changeInfo.newParameters.count { it.isNewParameter } > 0 && element.isImplicit) {
            val constructor = element.parent as KtSecondaryConstructor
            elementToWorkWith = constructor.replaceImplicitDelegationCallWithExplicit(isThisCall)
        }

        delegate.processUsage(changeInfo, elementToWorkWith, allUsages)

        if (changeInfo.newParameters.isEmpty() && !isThisCall && !elementToWorkWith.isImplicit) {
            (elementToWorkWith.parent as? KtSecondaryConstructor)?.colon?.delete()
            elementToWorkWith.replace(KtPsiFactory(element.project).creareDelegatedSuperTypeEntry(""))
        }

        return null
    }
}