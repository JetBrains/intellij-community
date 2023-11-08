// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
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
        val type = K2MoveModel.create(elements, targetContainer)
        K2MoveDialog(project, type).show()
    }
}