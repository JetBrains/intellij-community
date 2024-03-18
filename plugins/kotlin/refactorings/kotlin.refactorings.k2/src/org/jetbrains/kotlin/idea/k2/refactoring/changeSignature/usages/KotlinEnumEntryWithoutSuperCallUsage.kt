// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages

import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfoBase
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtInitializerList
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry

class KotlinEnumEntryWithoutSuperCallUsage(enumEntry: KtEnumEntry) : KotlinBaseChangeSignatureUsage, UsageInfo(enumEntry) {
    private var callProcessor: KotlinFunctionCallUsage? = null
    fun preprocess(changeInfo: KotlinChangeInfoBase, element: KtElement) {
        if (changeInfo.newParameters.isNotEmpty()) {
            val psiFactory = KtPsiFactory(element.project)
            val delegatorToSuperCall = (element.addAfter(
                psiFactory.createEnumEntryInitializerList(), (element as? KtEnumEntry)?.nameIdentifier
            ) as KtInitializerList).initializers[0] as KtSuperTypeCallEntry

            callProcessor = KotlinFunctionCallUsage(delegatorToSuperCall, changeInfo.method)
        }
    }

    override fun processUsage(
        changeInfo: KotlinChangeInfoBase, element: KtElement, allUsages: Array<out UsageInfo>
    ): KtElement? {
        val callProcessor = callProcessor
        if (callProcessor != null) {
            return callProcessor.processUsage(changeInfo, callProcessor.element as KtElement, allUsages)
        }

        return null
    }
}
