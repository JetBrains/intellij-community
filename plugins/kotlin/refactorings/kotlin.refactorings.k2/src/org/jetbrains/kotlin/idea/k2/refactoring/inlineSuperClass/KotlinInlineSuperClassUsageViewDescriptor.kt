// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inlineSuperClass

import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtClass

class KotlinInlineSuperClassUsageViewDescriptor(private val sourceClass: KtClass) : UsageViewDescriptor {
    override fun getElements(): Array<PsiElement> = arrayOf(sourceClass)
    override fun getProcessedElementsHeader(): String = KotlinBundle.message("inline.super.class.header")
    override fun getCodeReferencesText(usagesCount: Int, filesCount: Int): String =
        KotlinBundle.message("inline.super.class.usages", UsageViewBundle.getReferencesString(usagesCount, filesCount))

    override fun getCommentReferencesText(usagesCount: Int, filesCount: Int): String? = null
}