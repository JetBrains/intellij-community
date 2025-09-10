// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.navigationToolbar

import com.intellij.ide.navigationToolbar.StructureAwareNavBarModelExtension
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.util.KotlinSingleClassFileAnalyzer
import org.jetbrains.kotlin.idea.projectView.KtDeclarationTreeNode.Companion.tryGetRepresentableText
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile

class KotlinNavBarModelExtension : StructureAwareNavBarModelExtension() {
    override val language: Language
        get() = KotlinLanguage.INSTANCE

    override fun acceptParentFromModel(psiElement: PsiElement?): Boolean =
        if (psiElement is KtFile) {
            KotlinSingleClassFileAnalyzer.getSingleClass(psiElement) == null
        } else {
            true
        }

    override fun adjustElement(psiElement: PsiElement): PsiElement =
        when {
            psiElement is KtDeclaration -> {
                psiElement
            }
            psiElement.containingFile is KtFile -> {
                KotlinSingleClassFileAnalyzer.getSingleClass(psiElement.containingFile as KtFile) ?: psiElement
            }
            else -> {
                psiElement
            }
        }

    override fun getPresentableText(item: Any?): String? =
        (item as? KtDeclaration)?.let {
            tryGetRepresentableText(it, renderReceiverType = false, renderArguments = false, renderReturnType = false)
        }
}
