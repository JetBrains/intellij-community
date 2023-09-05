// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

class K2MoveFilesHandler : MoveFileHandler() {
    override fun canProcessElement(element: PsiFile): Boolean {
        return element is KtFile
    }

    override fun findUsages(
        psiFile: PsiFile,
        newParent: PsiDirectory,
        searchInComments: Boolean,
        searchInNonJavaFiles: Boolean
    ): List<UsageInfo> {
        require(psiFile is KtFile) { "Can only find usages from Kotlin files" }
        val newPkgName = JavaDirectoryService.getInstance().getPackage(newParent)?.kotlinFqName ?: return emptyList()
        return K2MoveSource.FileSource(psiFile).findusages(searchInComments, searchInNonJavaFiles, newPkgName)
    }

    override fun prepareMovedFile(file: PsiFile, moveDestination: PsiDirectory, oldToNewMap: MutableMap<PsiElement, PsiElement>) {
        require(file is KtFile) { "Can only prepare Kotlin files" }
        file.updatePackageDirective(moveDestination)
        val declarations = file.declarationsForUsageSearch
        declarations.forEach { oldToNewMap[it] = it }
    }

    private fun KtFile.updatePackageDirective(destination: PsiDirectory) {
        val newPackageName = JavaDirectoryService.getInstance().getPackage(destination)?.kotlinFqName ?: return
        if (newPackageName.isRoot) {
            packageDirective?.delete()
        } else {
            val newPackageDirective = KtPsiFactory(project).createPackageDirective(newPackageName)
            packageDirective?.replace(newPackageDirective)
        }
    }

    override fun updateMovedFile(file: PsiFile) { }

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun retargetUsages(usageInfos: List<UsageInfo>, oldToNewMap: MutableMap<PsiElement, PsiElement>): Unit = allowAnalysisOnEdt {
       retargetUsagesAfterMove(usageInfos, oldToNewMap)
    }
}