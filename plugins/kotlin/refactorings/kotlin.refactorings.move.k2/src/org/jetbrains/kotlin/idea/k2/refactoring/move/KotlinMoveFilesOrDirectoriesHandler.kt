// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReference
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesHandler
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

class KotlinMoveFilesOrDirectoriesHandler : MoveFilesOrDirectoriesHandler() {
    private fun adjustElements(elements: Array<out PsiElement>): Array<PsiFileSystemItem>? {
        return elements.map {
            when {
                it is PsiFile -> it
                it is PsiDirectory -> it
                it is KtClassOrObject && it.parent is KtFile -> it.parent as KtFile
                else -> return null
            }
        }.toTypedArray()
    }

    override fun canMove(elements: Array<out PsiElement>, targetContainer: PsiElement?, reference: PsiReference?): Boolean {
        val adjustedElements = adjustElements(elements) ?: return false
        if (adjustedElements.none { it is KtFile }) return false
        return super.canMove(adjustedElements, targetContainer, reference)
    }

    override fun adjustForMove(
        project: Project,
        sourceElements: Array<out PsiElement>,
        targetElement: PsiElement?
    ): Array<PsiFileSystemItem>? {
        return adjustElements(sourceElements)
    }
}