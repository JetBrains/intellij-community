// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer
import org.jetbrains.kotlin.idea.base.psi.unquoteKotlinIdentifier
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPrimaryConstructor

class KotlinMemberInplaceRenameHandler : MemberInplaceRenameHandler() {
    private class RenamerImpl(
        elementToRename: PsiNamedElement,
        substitutedElement: PsiElement?,
        editor: Editor,
        currentName: String,
        oldName: String
    ) : MemberInplaceRenamer(elementToRename, substitutedElement, editor, currentName, oldName) {
        override fun isIdentifier(newName: String?, language: Language?): Boolean {
            if (newName == "" && (variable as? KtObjectDeclaration)?.isCompanion() == true) return true
            return super.isIdentifier(newName, language)
        }

        override fun acceptReference(reference: PsiReference): Boolean {
            val refElement = reference.element
            val textRange = reference.rangeInElement
            val referenceText = refElement.text.substring(textRange.startOffset, textRange.endOffset).unquoteKotlinIdentifier()
            return referenceText == myElementToRename.name
        }

        override fun startsOnTheSameElement(handler: RefactoringActionHandler?, element: PsiElement?): Boolean {
            return variable == element && (handler is MemberInplaceRenameHandler || handler is KotlinRenameDispatcherHandler)
        }

        override fun createInplaceRenamerToRestart(variable: PsiNamedElement, editor: Editor, initialName: String): VariableInplaceRenamer {
            return RenamerImpl(variable, substituted, editor, initialName, myOldName)
        }
    }

    private fun PsiElement.substitute(): PsiElement {
        if (this is KtPrimaryConstructor) return getContainingClassOrObject()
        return this
    }

    override fun createMemberRenamer(element: PsiElement, elementToRename: PsiNameIdentifierOwner, editor: Editor): MemberInplaceRenamer {
        val currentElementToRename = elementToRename.substitute() as PsiNameIdentifierOwner
        val nameIdentifier = currentElementToRename.nameIdentifier

        // Move caret if constructor range doesn't intersect with the one of the containing class
        val offset = editor.caretModel.offset
        val editorPsiFile = PsiDocumentManager.getInstance(element.project).getPsiFile(editor.document)
        if (nameIdentifier != null && editorPsiFile == elementToRename.containingFile && elementToRename is KtPrimaryConstructor && offset !in nameIdentifier.textRange && offset in elementToRename.textRange) {
            editor.caretModel.moveToOffset(nameIdentifier.textOffset)
        }

        val currentName = nameIdentifier?.text ?: ""
        return RenamerImpl(currentElementToRename, element, editor, currentName, currentName)
    }

    override fun isAvailable(element: PsiElement?, editor: Editor, file: PsiFile): Boolean {
        if (!editor.settings.isVariableInplaceRenameEnabled) return false
        val currentElement = element?.substitute() as? KtNamedDeclaration ?: return false
        return currentElement.nameIdentifier != null && !KotlinVariableInplaceRenameHandler.isInplaceRenameAvailable(currentElement)
    }
}
