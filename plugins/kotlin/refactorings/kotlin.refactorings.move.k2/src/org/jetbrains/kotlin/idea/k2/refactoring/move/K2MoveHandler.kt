// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.parentOfTypes
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.MoveHandlerDelegate
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.core.getPackage
import org.jetbrains.kotlin.idea.k2.refactoring.move.ui.K2MoveDialog
import org.jetbrains.kotlin.idea.k2.refactoring.move.ui.K2MoveModel
import org.jetbrains.kotlin.idea.k2.refactoring.move.ui.K2MoveSourceModel
import org.jetbrains.kotlin.idea.k2.refactoring.move.ui.K2MoveTargetModel
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class K2MoveHandler : MoveHandlerDelegate() {
    override fun supportsLanguage(language: Language): Boolean = language == KotlinLanguage.INSTANCE

    override fun canMove(elements: Array<out PsiElement>, targetContainer: PsiElement?, reference: PsiReference?): Boolean {
        if (elements.any { it !is KtElement}) return false
        if (targetContainer != null && !targetContainer.isValidTarget()) return false
        return true
    }

    private fun PsiElement.isValidTarget(): Boolean {
        return when (this) {
            is PsiDirectory -> getPackage() != null
            else -> true
        }
    }

    override fun tryToMove(
        element: PsiElement,
        project: Project,
        dataContext: DataContext,
        reference: PsiReference?,
        editor: Editor
    ): Boolean {
        fun PsiElement.findElementToMove(): PsiElement? {
            val candidate = parentOfTypes(KtNamedDeclaration::class, KtFile::class, withSelf = true)
            if (candidate is KtConstructor<*>) return candidate.parent.findElementToMove()
            return candidate
        }

        val elementToMove = element.findElementToMove() ?: return false
        val elements = arrayOf(elementToMove)
        return if (canMove(elements, null, reference)) {
            doMove(project, elements, null, null)
            true
        } else false
    }

    override fun doMove(project: Project, elements: Array<out PsiElement>, targetContainer: PsiElement?, callback: MoveCallback?) {
        val ktElements = elements.map { it as KtElement }
        val type = when (targetContainer) {
            is PsiDirectory -> {
                val source = K2MoveSourceModel.FileSource(ktElements.map { it.correctForProjectView() }.filterIsInstance<KtFile>().toSet())
                val target = K2MoveTargetModel.SourceDirectory(targetContainer)
                K2MoveModel.Files(source, target)
            }
            is KtFile -> {
                val elementsToSearch = ktElements.flatMap {
                    when (it) {
                        is KtNamedDeclaration -> listOf(it)
                        is KtFile -> it.declarations.filterIsInstance<KtNamedDeclaration>()
                        else -> emptyList()
                    }
                }.toSet()
                val source = K2MoveSourceModel.ElementSource(elementsToSearch)
                val target = K2MoveTargetModel.File(targetContainer.correctForProjectView() as KtFile)
                K2MoveModel.Members(source, target)
            }
            else -> {
                val elementsToSearch = ktElements.flatMap {
                    when (it) {
                        is KtNamedDeclaration -> listOf(it)
                        is KtFile -> it.declarations.filterIsInstance<KtNamedDeclaration>()
                        else -> emptyList()
                    }
                }.toSet()
                val source = K2MoveSourceModel.ElementSource(elementsToSearch)
                val file = ktElements.firstOrNull()?.containingKtFile ?: error("No default target found")
                val target = K2MoveTargetModel.File(file)
                K2MoveModel.Members(source, target)
            }
        }

        K2MoveDialog(project, type).show()
    }

    /**
     * When moving elements to or from a class we expect the user to want to move them to the containing file instead
     */
    private fun KtElement.correctForProjectView(): KtElement {
        val containingFile = containingKtFile
        if (containingFile.declarations.singleOrNull() == this) return containingFile
        return this
    }
}