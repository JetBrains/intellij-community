// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages

import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfoBase
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

internal class KotlinDataClassComponentUsage(
    calleeExpression: KtSimpleNameExpression,
    private val newName: String
) : UsageInfo(calleeExpression), KotlinBaseUsage {
    override fun processUsage(
      changeInfo: KotlinChangeInfoBase,
      element: KtElement,
      allUsages: Array<out UsageInfo>
    ): KtElement? {
        val element = element as? KtSimpleNameExpression ?: return null
        return element.replace(KtPsiFactory(element.project).createExpression(newName)) as KtElement?
    }
}