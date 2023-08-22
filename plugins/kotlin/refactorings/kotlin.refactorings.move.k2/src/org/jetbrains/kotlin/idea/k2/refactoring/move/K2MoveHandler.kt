// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.MoveHandlerDelegate
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class K2MoveHandler : MoveHandlerDelegate() {
    override fun supportsLanguage(language: Language): Boolean = language == KotlinLanguage.INSTANCE

    override fun canMove(elements: Array<out PsiElement>, targetContainer: PsiElement?, reference: PsiReference?): Boolean {
        return elements.all { it is KtElement } && (targetContainer is KtElement || targetContainer is PsiDirectory)
    }

    override fun doMove(project: Project, elements: Array<out PsiElement>, targetContainer: PsiElement?, callback: MoveCallback?) {
        if (targetContainer == null) return

        val type = if (targetContainer is PsiDirectory) {
            val source = K2MoveSource.FileSource(elements.map { it.correctForProjectView() }.filterIsInstance<KtFile>().toSet())
            val target = K2MoveTarget.SourceDirectory(targetContainer)
            K2MoveDescriptor.Files(source, target)
        } else {
            val elementsToSearch = elements.flatMap {
                when (it) {
                    is KtNamedDeclaration -> listOf(it)
                    is KtFile -> it.declarations.filterIsInstance<KtNamedDeclaration>()
                    else -> emptyList()
                }
            }.toSet()
            val source = K2MoveSource.ElementSource(elementsToSearch)
            val target = K2MoveTarget.File(targetContainer.correctForProjectView() as KtFile)
            K2MoveDescriptor.Members(source, target)
        }

        K2MoveDialog(project, type).show()
    }

    // When moving elements to or from a class we expect the user to want to move them to the containing file instead
    private fun PsiElement.correctForProjectView() = if (this is KtNamedDeclaration) containingKtFile else this
}