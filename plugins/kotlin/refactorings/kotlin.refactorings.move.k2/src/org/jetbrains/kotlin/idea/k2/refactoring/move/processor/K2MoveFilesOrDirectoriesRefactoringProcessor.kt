// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveRenameUsageInfo.Companion.unMarkNonUpdatableUsages
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration

/**
 * K2 move refactoring processor that moves whole files or sets of files. The main difference between this processor and
 * [K2MoveDeclarationsRefactoringProcessor] is that this processor moves the file as a whole and adjusts imports later while
 * [K2MoveDeclarationsRefactoringProcessor] moves the individual declarations and generates the imports from them. Most of the Kotlin
 * specific logic for this refactoring processor is implemented in [K2MoveFilesHandler].
 */
class K2MoveFilesOrDirectoriesRefactoringProcessor(descriptor: K2MoveDescriptor.Files) : MoveFilesOrDirectoriesProcessor(
    descriptor.project,
    descriptor.source.elements.toTypedArray(),
    runWriteAction { descriptor.target.getOrCreateTarget() as PsiDirectory },
    descriptor.searchReferences,
    descriptor.searchInComments,
    descriptor.searchForText,
    MoveCallback { },
    Runnable { }
)

class K2MoveFilesHandler : MoveFileHandler() {
    override fun canProcessElement(element: PsiFile): Boolean {
        if (!Registry.`is`("kotlin.k2.smart.move")) return false
        return element is KtFile
    }

    override fun findUsages(
        psiFile: PsiFile,
        newParent: PsiDirectory,
        searchInComments: Boolean,
        searchInNonJavaFiles: Boolean
    ): List<UsageInfo> {
        require(psiFile is KtFile) { "Can only find usages from Kotlin files" }
        return if (psiFile.requiresPackageUpdate) {
            val newPkgName = JavaDirectoryService.getInstance().getPackage(newParent)?.kotlinFqName ?: return emptyList()
            psiFile.findUsages(searchInComments, searchInNonJavaFiles, newPkgName)
        } else emptyList() // don't need to update usages when package doesn't change
    }

    override fun detectConflicts(
        conflicts: MultiMap<PsiElement, String>,
        elementsToMove: Array<out PsiElement>,
        usages: Array<out UsageInfo>,
        targetDirectory: PsiDirectory
    ) {
        val targetPkgFqn = JavaDirectoryService.getInstance().getPackage(targetDirectory)?.kotlinFqName ?: FqName.ROOT
        conflicts.putAllValues(findAllMoveConflicts(
            elementsToMove.filterIsInstance<KtFile>().toSet(),
            targetDirectory,
            targetPkgFqn,
            usages.filterIsInstance<MoveRenameUsageInfo>()
        ))
        // after conflict checking, we don't need non-updatable usages anymore
        unMarkNonUpdatableUsages(elementsToMove.filterIsInstance<KtElement>().toSet())
    }

    override fun prepareMovedFile(file: PsiFile, moveDestination: PsiDirectory, oldToNewMap: MutableMap<PsiElement, PsiElement>) {
        require(file is KtFile) { "Can only prepare Kotlin files" }
        if (file.requiresPackageUpdate) {
            file.updatePackageDirective(moveDestination)
        }
        val declarations = file.allDeclarationsToUpdate
        declarations.forEach { oldToNewMap[it] = it } // to pass files that are moved through MoveFileHandler API
    }

    private val KtFile.requiresPackageUpdate: Boolean
        get() {
            val containingDirectory = containingDirectory ?: return true
            val directoryPkg = JavaDirectoryService.getInstance().getPackage(containingDirectory)
            return directoryPkg?.kotlinFqName == packageFqName
        }

    override fun updateMovedFile(file: PsiFile) {}

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun retargetUsages(usageInfos: List<UsageInfo>, oldToNewMap: Map<PsiElement, PsiElement>): Unit = allowAnalysisOnEdt {
        @Suppress("UNCHECKED_CAST")
        retargetUsagesAfterMove(usageInfos, oldToNewMap as Map<KtNamedDeclaration, KtNamedDeclaration>)
    }
}