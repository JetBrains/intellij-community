// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.MoveHandlerDelegate
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class K2MoveDeclarationsHandler : MoveHandlerDelegate() {
    override fun supportsLanguage(language: Language): Boolean = language == KotlinLanguage.INSTANCE

    override fun canMove(elements: Array<out PsiElement>?, targetContainer: PsiElement?, reference: PsiReference?): Boolean {
        return true
    }

    override fun doMove(project: Project, elements: Array<out PsiElement>, targetContainer: PsiElement?, callback: MoveCallback?) {
        val elementsToSearch = elements.flatMapTo(LinkedHashSet()) {
            when (it) {
                is KtNamedDeclaration -> listOf(it)
                is KtFile -> it.declarations.filterIsInstance<KtNamedDeclaration>()
                else -> emptyList()
            }
        }
        K2MoveDeclarationsDialog(project, elementsToSearch, targetContainer).show()
    }
}