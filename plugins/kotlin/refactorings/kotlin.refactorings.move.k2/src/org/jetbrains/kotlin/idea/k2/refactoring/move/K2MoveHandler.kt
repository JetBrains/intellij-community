// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.parentOfTypes
import com.intellij.psi.util.startOffset
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.MoveHandlerDelegate
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.codeinsight.utils.KotlinSupportAvailability
import org.jetbrains.kotlin.idea.k2.refactoring.move.ui.K2MoveDialog
import org.jetbrains.kotlin.idea.k2.refactoring.move.ui.K2MoveModel
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import java.lang.Boolean.getBoolean

class K2MoveHandler : MoveHandlerDelegate() {
    override fun supportsLanguage(language: Language): Boolean = language == KotlinLanguage.INSTANCE

    override fun canMove(elements: Array<out PsiElement>, targetContainer: PsiElement?, reference: PsiReference?): Boolean {
        if (elements.any { it !is KtElement && it !is PsiFile && it !is PsiClass && it !is PsiDirectory }) return false
        if (elements.none { it is KtElement }) return false // only handle the refactoring if it includes Kotlin elements
        (elements.firstOrNull()?.containingFile as? KtFile)?.let { if (!KotlinSupportAvailability.isSupported(it)) return false }

        // We allow declarations to be moved into non-source roots here,
        // but in this case they will be moved as files rather than individual declarations.
        return true
    }

    override fun tryToMove(
        element: PsiElement,
        project: Project,
        dataContext: DataContext,
        reference: PsiReference?,
        editor: Editor
    ): Boolean {
        val elementToMove = element.findElementToMove(editor) ?: return false
        val elements = arrayOf(elementToMove)
        return if (canMove(elements, null, reference)) {
            doMoveWithCheck(project, elements, null, editor)
            true
        } else false
    }

    override fun doMove(project: Project, elements: Array<out PsiElement>, targetContainer: PsiElement?, callback: MoveCallback?) {
        doMoveWithCheck(project, elements, targetContainer, null)
    }

    private fun doMoveWithCheck(
        project: Project,
        elements: Array<out PsiElement>,
        targetContainer: PsiElement?,
        editor: Editor?
    ) {
        val type = K2MoveModel.create(elements, targetContainer, editor) ?: return
        K2MoveDialog(project, type).apply {
            if (getBoolean("ide.performance.skip.refactoring.dialogs"))
                performOKAction()
            else
                show()
        }
    }

    internal fun PsiElement.findElementToMove(editor: Editor): PsiElement? {
        // choose the previous declaration when the caret is put right after it
        if (this is PsiWhiteSpace && startOffset == editor.caretModel.offset) {
            prevSibling?.findElementToMove(editor)?.let { return it }
        }
        // try the last element in the file when the caret is at the end of the file
        if (this is KtFile && editor.caretModel.offset == editor.document.textLength) {
            lastChild?.findElementToMove(editor)?.let { return it }
        }
        val candidate = parentOfTypes(KtNamedDeclaration::class, KtFile::class, PsiFile::class, PsiDirectory::class, withSelf = true)
        if (candidate is KtConstructor<*>) return candidate.parent.findElementToMove(editor)
        return candidate
    }
}