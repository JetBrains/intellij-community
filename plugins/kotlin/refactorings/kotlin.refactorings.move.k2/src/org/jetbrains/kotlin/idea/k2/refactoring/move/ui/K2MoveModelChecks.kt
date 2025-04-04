// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.kotlin.idea.base.resources.BUNDLE
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens.ABSTRACT_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.OPEN_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.OVERRIDE_KEYWORD
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClass

internal val k2MoveModelChecks: List<K2MoveModelCheck> = listOf(
    NestedDeclarationIsMovedAloneCheck,
    NestedDeclarationTypeCheck,
    NoEnumEntriesCheck,
    MultiFileMoveTargetIsDirectoryCheck,
    MultiFileMoveNoDeclarationsCheck,
    FunctionModalityCheck,
)

internal sealed class K2MoveModelCheck(
    @field:[NonNls PropertyKey(resourceBundle = BUNDLE)]
    val cannotRefactorMessageKey: String
) {
    abstract fun isMoveAllowed(elementsToMove: List<PsiElement>, targetContainer: PsiElement?): Boolean

    fun showErrorHint(project: Project, editor: Editor?) {
        val message = RefactoringBundle.getCannotRefactorMessage(
            KotlinBundle.message(cannotRefactorMessageKey)
        )
        CommonRefactoringUtil.showErrorHint(project, editor, message, MOVE_DECLARATIONS, null)
    }
}

private object NestedDeclarationIsMovedAloneCheck : K2MoveModelCheck("text.move.declaration.only.support.for.single.elements") {
    override fun isMoveAllowed(elementsToMove: List<PsiElement>, targetContainer: PsiElement?): Boolean {
        if (!containNestedDeclarations(elementsToMove)) return true
        return elementsToMove.size == 1
    }
}

private object NestedDeclarationTypeCheck : K2MoveModelCheck("text.move.declaration.only.support.for.some.nested.declarations") {
    override fun isMoveAllowed(elementsToMove: List<PsiElement>, targetContainer: PsiElement?): Boolean {
        if (!containNestedDeclarations(elementsToMove)) return true
        return elementsToMove.all { element ->
            element is KtClassOrObject || element is KtNamedFunction || element is KtProperty
        }
    }
}

private object NoEnumEntriesCheck : K2MoveModelCheck("text.move.declaration.no.support.for.enums") {
    override fun isMoveAllowed(elementsToMove: List<PsiElement>, targetContainer: PsiElement?): Boolean {
        return elementsToMove.none { it is KtEnumEntry }
    }
}

private object MultiFileMoveTargetIsDirectoryCheck : K2MoveModelCheck("text.move.file.no.support.for.file.target") {
    override fun isMoveAllowed(elementsToMove: List<PsiElement>, targetContainer: PsiElement?): Boolean {
        if (!isMultiFileMove(elementsToMove)) return true
        if (targetContainer == null) return true
        return targetContainer is PsiDirectory
    }
}

private object MultiFileMoveNoDeclarationsCheck : K2MoveModelCheck("text.move.declaration.no.support.for.multi.file") {
    override fun isMoveAllowed(
        elementsToMove: List<PsiElement>,
        targetContainer: PsiElement?
    ): Boolean {
        if (!isMultiFileMove(elementsToMove)) return true
        return elementsToMove.none { it is KtNamedDeclaration && !it.isSingleClassContainer() }
    }
}

private object FunctionModalityCheck : K2MoveModelCheck("text.move.declaration.no.support.for.open.override.function") {
    override fun isMoveAllowed(
        elementsToMove: List<PsiElement>,
        targetContainer: PsiElement?
    ): Boolean = elementsToMove.none {
        it is KtNamedFunction && it.hasForbiddenModalityOrOverride()
    }

    private fun KtNamedFunction.hasForbiddenModalityOrOverride(): Boolean =
        hasModifier(OVERRIDE_KEYWORD) || hasModifier(OPEN_KEYWORD) || hasModifier(ABSTRACT_KEYWORD)
                || containingClass()?.isInterface() == true // abstract without body or open with body
}

private val MOVE_DECLARATIONS: String
    @Nls
    get() = KotlinBundle.message("text.move.declarations")
