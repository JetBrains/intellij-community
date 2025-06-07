// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration

internal fun String.isValidKotlinFile(): Boolean {
    return endsWith(KotlinLanguage.INSTANCE.associatedFileType?.defaultExtension ?: return false) || endsWith(".kts")
}

internal fun isMultiFileMove(movedElements: List<PsiElement>): Boolean = movedElements.toFileElements().toSet().size > 1

internal fun isSingleFileMove(movedElements: List<PsiElement>): Boolean = movedElements.all { it is KtNamedDeclaration }
        || movedElements.singleOrNull() is KtFile

internal fun PsiElement?.isSingleClassContainer(): Boolean {
    if (this !is KtClassOrObject) return false
    val file = parent as? KtFile ?: return false
    return this == file.declarations.singleOrNull()
}

internal fun isInSourceRoot(project: Project, declarations: List<PsiElement>, targetContainer: PsiElement?): Boolean {
    val fileIndex = ProjectFileIndex.getInstance(project)
    if (declarations.toFileElements().toSet().any { !fileIndex.isInSourceContent(it.virtualFile) }) return false
    if (targetContainer == null || targetContainer is PsiDirectory) return true
    val targetFile = targetContainer.containingFile?.virtualFile ?: return false
    return fileIndex.isInSourceContent(targetFile)
}

internal fun List<PsiElement>.toFileElements(): List<PsiFileSystemItem> = map { it as? PsiDirectory ?: it.containingFile }

internal fun findSourceFileNameByMovedElements(elementsToMove: List<PsiElement>): String {
    val firstElem = elementsToMove.firstOrNull() as KtElement
    return when (firstElem) {
        is KtFile -> firstElem.name
        is KtNamedDeclaration -> "${firstElem.name}.${KotlinLanguage.INSTANCE.associatedFileType?.defaultExtension}"
        else -> error("Element to move should be a file or declaration")
    }
}

internal fun containNestedDeclarations(elementsToMove: List<PsiElement>): Boolean =
    elementsToMove.any { it.parentOfType<KtNamedDeclaration>(withSelf = false) != null }
