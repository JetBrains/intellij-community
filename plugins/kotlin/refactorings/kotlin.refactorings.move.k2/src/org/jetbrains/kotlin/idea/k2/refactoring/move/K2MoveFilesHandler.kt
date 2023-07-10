// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

class K2MoveFilesHandler : MoveFileHandler() {
    override fun canProcessElement(element: PsiFile): Boolean {
        return element is KtFile
    }

    override fun findUsages(
        psiFile: PsiFile?,
        newParent: PsiDirectory?,
        searchInComments: Boolean,
        searchInNonJavaFiles: Boolean
    ): List<UsageInfo> {
        return emptyList()
    }

    override fun retargetUsages(usageInfos: MutableList<UsageInfo>, oldToNewMap: MutableMap<PsiElement, PsiElement>) {

    }

    override fun prepareMovedFile(file: PsiFile?, moveDestination: PsiDirectory, oldToNewMap: MutableMap<PsiElement, PsiElement>) {
        require(file is KtFile) { "Can only process Kotlin files" }
        val newPackageName = JavaDirectoryService.getInstance().getPackage(moveDestination)?.kotlinFqName ?: return
        val newPackageDirective = KtPsiFactory(file.project).createPackageDirective(newPackageName)
        file.packageDirective?.replace(newPackageDirective)
    }

    override fun updateMovedFile(file: PsiFile) {
        require(file is KtFile) { "Can only process Kotlin files" }
    }
}