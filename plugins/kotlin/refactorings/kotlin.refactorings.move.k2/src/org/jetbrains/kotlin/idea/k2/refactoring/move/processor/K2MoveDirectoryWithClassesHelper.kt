// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.startOffset
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.move.moveClassesOrPackages.MoveDirectoryWithClassesHelper
import com.intellij.refactoring.move.moveClassesOrPackages.MoveDirectoryWithClassesProcessor
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.Function
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

/**
 * Called when moving packages.
 */
class K2MoveDirectoryWithClassesHelper : MoveDirectoryWithClassesHelper() {
    private val moveFileHandler = K2MoveFilesHandler()

    override fun findUsages(
        filesToMove: MutableCollection<out PsiFile>,
        directoriesToMove: Array<out PsiDirectory>,
        result: MutableCollection<in UsageInfo>,
        searchInComments: Boolean,
        searchInNonJavaFiles: Boolean,
        project: Project?
    ) {
        // unused because we need the target directory to build non-code usage infos
    }

    override fun findUsages(
        filesToMove: MutableMap<VirtualFile, MoveDirectoryWithClassesProcessor.TargetDirectoryWrapper>,
        directoriesToMove: Array<out PsiDirectory>,
        result: MutableCollection<in UsageInfo>,
        searchInComments: Boolean,
        searchInNonJavaFiles: Boolean,
        project: Project
    ) {
        val psiManager = PsiManager.getInstance(project)
        filesToMove.forEach { (file, targetDirWrapper) ->
            val ktFile = psiManager.findFile(file) as? KtFile ?: return@forEach
            if (moveFileHandler.needsUpdate(ktFile)) moveFileHandler.markRequiresUpdate(ktFile)
            val rootDirPkg = JavaDirectoryService.getInstance().getPackage(targetDirWrapper.getRootDirectory())
            if (rootDirPkg != null) {
                val relativePkgName = targetDirWrapper.getRelativePathFromRoot().replace("/".toRegex(), ".")
                val packageName = if (!relativePkgName.isEmpty()) {
                    StringUtil.getQualifiedName(rootDirPkg.qualifiedName, relativePkgName)
                } else rootDirPkg.qualifiedName
                result.addAll(ktFile.findUsages(searchInComments, searchInNonJavaFiles, FqName(packageName)))
            }
        }
    }

    override fun preprocessUsages(
        project: Project,
        files: Set<PsiFile>,
        infos: Ref<Array<out UsageInfo>>,
        targetDirectory: PsiDirectory?,
        conflicts: MultiMap<PsiElement, String>
    ) {
        // processing kotlin usages from Java declarations will result in non-deterministic retargeting of the references
        // to fix this, all usages are sorted by start offset
        infos.get().sortedBy { it.element?.startOffset ?: -1 }
        if (targetDirectory != null) { // TODO probably this should never be null but it happens when there are multiple source roots
            moveFileHandler.detectConflicts(conflicts, files.filterIsInstance<KtFile>().toTypedArray(), infos.get(), targetDirectory)
        }
    }

    override fun beforeMove(psiFile: PsiFile?) {
        // actually logic that runs before the move is implemented in `move` method
    }

    override fun move(
        file: PsiFile,
        moveDestination: PsiDirectory,
        oldToNewElementsMapping: MutableMap<PsiElement, PsiElement>,
        movedFiles: MutableList<in PsiFile>,
        listener: RefactoringElementListener?
    ): Boolean {
        if (file !is KtFile) return false
        moveFileHandler.prepareMovedFile(file, moveDestination, oldToNewElementsMapping)
        MoveFilesOrDirectoriesUtil.doMoveFile(file, moveDestination)
        return true
    }

    override fun afterMove(newElement: PsiElement) {
        if (newElement !is KtFile) return
        moveFileHandler.updateMovedFile(newElement)
    }

    override fun retargetUsages(usages: MutableList<UsageInfo>, oldToNewMap: Map<PsiElement, PsiElement>) {
        val usagesToProcess = usages.filterIsInstance<K2MoveRenameUsageInfo>()
        moveFileHandler.retargetUsages(usagesToProcess, oldToNewMap)
        usages.removeAll(usagesToProcess)
    }

    override fun postProcessUsages(usages: Array<out UsageInfo>?, newDirMapper: Function<in PsiDirectory, out PsiDirectory>?) {

    }
}