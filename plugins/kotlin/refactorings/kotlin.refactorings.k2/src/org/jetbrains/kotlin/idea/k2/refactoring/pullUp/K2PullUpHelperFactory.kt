// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pullUp

import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.memberPullUp.PullUpData
import com.intellij.refactoring.memberPullUp.PullUpHelper
import com.intellij.refactoring.memberPullUp.PullUpHelperFactory
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allowAnalysisFromWriteActionInEdt
import org.jetbrains.kotlin.idea.refactoring.pullUp.EmptyPullUpHelper
import org.jetbrains.kotlin.idea.refactoring.pullUp.toKtDeclarationWrapperAware
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class K2PullUpHelperFactory : PullUpHelperFactory {
    private fun PullUpData.toKotlinPullUpData(): K2PullUpData? {
        val sourceClass = sourceClass.unwrapped as? KtClassOrObject ?: return null
        val targetClass = targetClass.unwrapped as? PsiNamedElement ?: return null
        val membersToMove = membersToMove
            .mapNotNull { it.toKtDeclarationWrapperAware() }
            .sortedBy { it.startOffset }
        return K2PullUpData(sourceClass, targetClass, membersToMove)
    }

    override fun createPullUpHelper(data: PullUpData): PullUpHelper<*> {
        if (!data.sourceClass.isInheritor(data.targetClass, true)) return EmptyPullUpHelper
        allowAnalysisFromWriteActionInEdt(data.sourceClass.unwrapped as KtElement) {
            data.toKotlinPullUpData()?.let { return K2PullUpHelper(data, it) }
        }

        return EmptyPullUpHelper
    }
}
