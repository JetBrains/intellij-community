// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.navigationToolbar

import com.intellij.ide.navigationToolbar.StructureAwareNavBarModelExtension
import com.intellij.ide.structureView.StructureViewTreeElement
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

    override fun findParentInModel(root: StructureViewTreeElement, psiElement: PsiElement): PsiElement? =
        findParentInModel(root, psiElement, root.value as? PsiElement)

    private fun findParentInModel(
        root: StructureViewTreeElement,
        psiElement: PsiElement,
        nearestPsiParent: PsiElement?,
    ): PsiElement? {
        val currentPsiParent = (root.value as? PsiElement) ?: nearestPsiParent
        for (child in childrenFromNodeAndProviders(root).filterIsInstance<StructureViewTreeElement>()) {
            if (child.value == psiElement) {
                return currentPsiParent
            }

            findParentInModel(child, psiElement, currentPsiParent)?.let { return it }
        }
        return null
    }

    override fun getPresentableText(item: Any?): String? =
        when (item) {
            is KtFile -> item.name
            is KtDeclaration -> tryGetRepresentableText(
                item,
                renderReceiverType = false,
                renderArguments = false,
                renderReturnType = false,
            )
            else -> null
        }
}
