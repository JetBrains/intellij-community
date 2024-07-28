// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefix
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefixOrRoot
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveRenameUsageInfo.Companion.unMarkNonUpdatableUsages
import org.jetbrains.kotlin.psi.CopyablePsiUserDataProperty
import org.jetbrains.kotlin.psi.KtFile

class K2MoveFilesOrDirectoriesRefactoringProcessor(descriptor: K2MoveOperationDescriptor.Files) : MoveFilesOrDirectoriesProcessor(
    descriptor.project,
    descriptor.sourceElements.toTypedArray(),
    runWriteAction { descriptor.moveDescriptors.first().target.getOrCreateTarget(descriptor.dirStructureMatchesPkg) as PsiDirectory }, // TODO how to do multi target move?
    descriptor.searchReferences,
    descriptor.searchInComments,
    descriptor.searchForText,
    descriptor.moveCallBack,
    Runnable { }
) {
    private fun PsiElement.allFiles(): List<KtFile> = when (this) {
        is PsiDirectory -> children.flatMap { it.allFiles() }
        is KtFile -> listOf(this)
        else -> emptyList()
    }

    override fun preprocessUsages(refUsages: Ref<Array<out UsageInfo>>): Boolean {
        val toContinue = super.preprocessUsages(refUsages)
        if (!toContinue) return false

        // after conflict checking, we don't need non-updatable usages anymore
        val elementsToMove = myElementsToMove.toList()
        unMarkNonUpdatableUsages(elementsToMove)
        val updatableUsages = K2MoveRenameUsageInfo.filterUpdatable(elementsToMove, refUsages.get())
        refUsages.set(updatableUsages.toTypedArray())
        val usagesByFile = updatableUsages.groupBy { (it as? K2MoveRenameUsageInfo)?.referencedElement?.containingFile }
        usagesByFile.forEach { file, usages -> myFoundUsages.replace(file, usages) }
        return true
    }
}

class K2MoveFilesHandler : MoveFileHandler() {
    /**
     * Stores whether a package of a file needs to be updated.
     */
    private var KtFile.packageNeedsUpdate: Boolean? by CopyablePsiUserDataProperty(Key.create("PACKAGE_NEEDS_UPDATE"))

    override fun canProcessElement(element: PsiFile): Boolean {
        if (!Registry.`is`("kotlin.k2.smart.move")) return false
        return element is KtFile
    }

    /**
     * Before moving files that need their package to be updated are marked. When a package doesn't match the directory structure of the
     * project, we don't try to update the package.
     */
    fun markRequiresUpdate(file: KtFile) {
        file.packageNeedsUpdate = true
    }

    fun needsUpdate(file: KtFile) = file.containingDirectory?.getFqNameWithImplicitPrefix() == file.packageFqName

    override fun findUsages(
        psiFile: PsiFile,
        newParent: PsiDirectory,
        searchInComments: Boolean,
        searchInNonJavaFiles: Boolean
    ): List<UsageInfo> {
        require(psiFile is KtFile) { "Can only find usages from Kotlin files" }
        return if (needsUpdate(psiFile)) {
            markRequiresUpdate(psiFile)
            val newPkgName = newParent.getFqNameWithImplicitPrefix() ?: return emptyList()
            psiFile.findUsages(searchInComments, searchInNonJavaFiles, newPkgName)
        } else emptyList() // don't need to update usages when package doesn't change
    }

    override fun detectConflicts(
        conflicts: MultiMap<PsiElement, String>,
        elementsToMove: Array<out PsiElement>,
        usages: Array<out UsageInfo>,
        targetDirectory: PsiDirectory
    ) {
        val targetPkgFqn = targetDirectory.getFqNameWithImplicitPrefixOrRoot()
        conflicts.putAllValues(findAllMoveConflicts(
            elementsToMove.filterIsInstance<KtFile>().toSet(),
            targetDirectory,
            targetPkgFqn,
            usages.filterIsInstance<MoveRenameUsageInfo>()
        ))
    }

    override fun prepareMovedFile(file: PsiFile, moveDestination: PsiDirectory, oldToNewMap: MutableMap<PsiElement, PsiElement>) {
        require(file is KtFile) { "Can only prepare Kotlin files" }
        if (file.packageNeedsUpdate == true && file.packageFqName != moveDestination.getFqNameWithImplicitPrefix()) {
            file.updatePackageDirective(moveDestination)
        }
        file.packageNeedsUpdate = null
        val declarations = file.allDeclarationsToUpdate
        declarations.forEach { oldToNewMap[it] = it } // to pass files that are moved through MoveFileHandler API
    }

    override fun updateMovedFile(file: PsiFile) {}

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun retargetUsages(usageInfos: List<UsageInfo>, oldToNewMap: Map<PsiElement, PsiElement>): Unit = allowAnalysisOnEdt {
        retargetUsagesAfterMove(usageInfos.toList(), oldToNewMap)
    }
}