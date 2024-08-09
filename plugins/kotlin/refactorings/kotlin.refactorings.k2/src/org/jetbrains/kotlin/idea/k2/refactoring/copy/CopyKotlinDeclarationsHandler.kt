// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.copy

import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.MoveDestination
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.copy.CopyFilesOrDirectoriesDialog
import com.intellij.usageView.UsageInfo
import com.intellij.util.IncorrectOperationException
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefix
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefixOrRoot
import org.jetbrains.kotlin.idea.core.packageMatchesDirectoryOrImplicit
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor.Directory
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveRenameUsageInfo.Companion.markInternalUsages
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveRenameUsageInfo.Companion.retargetInternalUsages
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveRenameUsageInfo.Companion.retargetInternalUsagesForCopyFile
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveRenameUsageInfo.Companion.unMarkAllUsages
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict.checkModuleDependencyConflictsForInternalUsages
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict.checkVisibilityConflictsForInternalUsages
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.createCopyTarget
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.createKotlinFile
import org.jetbrains.kotlin.idea.refactoring.checkConflictsInteractively
import org.jetbrains.kotlin.idea.refactoring.copy.*
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.sourceRoot
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

class CopyKotlinDeclarationsHandler : AbstractCopyKotlinDeclarationsHandler() {
    override fun createFile(
        targetFileName: String, targetDirectory: PsiDirectory
    ): KtFile = createKotlinFile(targetFileName, targetDirectory)

    private data class TargetData(
        val openInEditor: Boolean, val newName: String, val targetDirWrapper: K2MoveTargetDescriptor, val targetSourceRoot: VirtualFile?
    )

    private data class SourceData(
        val project: Project,
        val singleElementToCopy: KtElement?,
        val elementsToCopy: List<KtNamedDeclaration>,
        val originalFile: KtFile,
        val initialTargetDirectory: PsiDirectory
    )

    private fun getTargetData(sourceData: SourceData): TargetData? {
        if (isUnitTestMode()) {
            val targetSourceRoot: VirtualFile = sourceData.initialTargetDirectory.sourceRoot ?: return null
            val newName: String = sourceData.project.copyNewName ?: sourceData.singleElementToCopy?.name ?: sourceData.originalFile.name
            if (sourceData.singleElementToCopy != null && newName.isEmpty()) return null
            return TargetData(
                openInEditor = false,
                newName = newName,
                targetDirWrapper = sourceData.initialTargetDirectory.toDirectory(),
                targetSourceRoot = targetSourceRoot
            )

        }

        val openInEditor: Boolean
        val newName: String?
        val targetDirWrapper: K2MoveTargetDescriptor?
        val targetSourceRoot: VirtualFile?

        val singleNamedSourceElement = sourceData.singleElementToCopy as? KtNamedDeclaration

        if (singleNamedSourceElement !== null) {
            val dialog = CopyKotlinDeclarationDialog(singleNamedSourceElement, sourceData.initialTargetDirectory, sourceData.project)
            dialog.title = copyCommandName
            if (!dialog.showAndGet()) return null

            openInEditor = dialog.openInEditor
            newName = dialog.newName
            targetDirWrapper = dialog.targetDirectory?.toDirectory(sourceData)
            targetSourceRoot = dialog.targetSourceRoot
        } else {
            val dialog = CopyFilesOrDirectoriesDialog(
                arrayOf(sourceData.originalFile), sourceData.initialTargetDirectory, sourceData.project,/*doClone = */false
            )
            if (!dialog.showAndGet()) return null
            openInEditor = dialog.openInEditor()
            newName = dialog.newName
            targetDirWrapper = dialog.targetDirectory?.toDirectory()
            targetSourceRoot = dialog.targetDirectory?.sourceRoot
        }

        if (targetDirWrapper == null || newName == null) return null

        if (sourceData.singleElementToCopy != null && newName.isEmpty()) return null

        return TargetData(
            openInEditor = openInEditor, newName = newName, targetDirWrapper = targetDirWrapper, targetSourceRoot = targetSourceRoot
        )
    }

    private fun MoveDestination.toDirectory(
        sourceData: SourceData
    ): Directory = Directory(FqName(targetPackage.qualifiedName), runWriteAction { getTargetDirectory(sourceData.initialTargetDirectory) })

    private fun PsiDirectory.toDirectory(
    ): Directory = Directory(getFqNameWithImplicitPrefixOrRoot(), this)

    private fun doCopyFiles(filesToCopy: Array<out PsiFileSystemItem>, initialTargetDirectory: PsiDirectory?) {
        if (filesToCopy.isEmpty()) return
        filesToCopy.first().project.executeCommand(copyCommandName) {
            if (copyFilesHandler.canCopy(filesToCopy)) {
                copyFilesHandler.doCopy(filesToCopy, initialTargetDirectory)
            }
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun doCopy(elements: Array<out PsiElement>, defaultTargetDirectory: PsiDirectory?) {

        if (elements.isEmpty()) return

        if (!canCopyDeclarations(elements)) {
            val sourceFiles = getSourceFiles(elements) ?: return
            return doCopyFiles(sourceFiles, defaultTargetDirectory)
        }

        val elementsToCopy = elements.mapNotNull { it.getCopyableElement() }
        if (elementsToCopy.isEmpty()) return

        val singleElementToCopy = elementsToCopy.singleOrNull()

        val originalFile = elementsToCopy.first().containingFile as KtFile
        val initialTargetDirectory = defaultTargetDirectory ?: originalFile.containingDirectory ?: return

        val project = initialTargetDirectory.project

        val sourceData = SourceData(
            project = project,
            singleElementToCopy = singleElementToCopy,
            elementsToCopy = elementsToCopy.filterIsInstance<KtNamedDeclaration>(),
            originalFile = originalFile,
            initialTargetDirectory = initialTargetDirectory
        )

        val targetData = getTargetData(sourceData) ?: return

        for (element in elementsToCopy) {
            analyzeInModalWindow(elementsToCopy.first(), RefactoringBundle.message("refactoring.preprocess.usages.progress")) {
                markInternalUsages(element, element)
            }
        }

        val conflicts: MultiMap<PsiElement, String> =
            analyzeInModalWindow(elementsToCopy.first(), RefactoringBundle.message("detecting.possible.conflicts")) {
                collectConflicts(sourceData, targetData)
            }

        project.checkConflictsInteractively(conflicts) {
            try {
                ApplicationManagerEx.getApplicationEx().runWriteActionWithCancellableProgressInDispatchThread(copyCommandName, project, null) {
                    project.executeCommand(copyCommandName) {
                        doRefactor(sourceData, targetData)
                    }
                }
            } finally {
                elements.filterIsInstance<KtElement>().forEach(::unMarkAllUsages)
            }
        }
    }

    private data class RefactoringResult(
        val targetFile: PsiFile, val copiedDeclaration: KtNamedDeclaration?, val restoredInternalUsages: List<UsageInfo>? = null
    )

    private fun doRefactor(sourceData: SourceData, targetData: TargetData) {

        try {
            val targetDirectory = runWriteAction {
                targetData.targetDirWrapper.getOrCreateTarget(dirStructureMatchesPkg = true) as PsiDirectory
            }

            val targetFileName =
                if (targetData.newName.contains(".")) targetData.newName else targetData.newName + "." + sourceData.originalFile.virtualFile.extension

            val isSingleDeclarationInFile =
                sourceData.singleElementToCopy is KtNamedDeclaration && sourceData.originalFile.declarations.singleOrNull() == sourceData.singleElementToCopy

            val fileToCopy = when {
                sourceData.singleElementToCopy is KtFile -> sourceData.singleElementToCopy
                isSingleDeclarationInFile -> sourceData.originalFile
                else -> null
            }

            var refactoringResult = if (fileToCopy !== null) {
                doRefactoringOnFile(fileToCopy, targetDirectory, targetFileName, isSingleDeclarationInFile)
            } else {
                val targetFile = getOrCreateTargetFile(sourceData.originalFile, targetDirectory, targetFileName)
                    ?: throw IncorrectOperationException("Could not create target file.")
                doRefactoringOnElement(sourceData, targetFile)
            }

            refactoringResult.copiedDeclaration?.let<KtNamedDeclaration, Unit> { newDeclaration ->
                if (targetData.newName == newDeclaration.name) return@let
                val selfReferences = ReferencesSearch.search(newDeclaration, LocalSearchScope(newDeclaration)).findAll()
                runWriteAction {
                    selfReferences.forEach { it.handleElementRename(targetData.newName) }
                    newDeclaration.setName(targetData.newName)
                }
            }

            if (targetData.openInEditor) {
                EditorHelper.openInEditor(refactoringResult.targetFile)
            }
        } catch (e: IncorrectOperationException) {
            ApplicationManager.getApplication().invokeLater(
                Runnable {
                    Messages.showMessageDialog(
                        sourceData.project,
                        e.message,
                        RefactoringBundle.message("error.title"),
                        Messages.getErrorIcon()
                    )
                },
                ModalityState.nonModal()
            )
        }
    }


    private fun doRefactoringOnFile(
        fileToCopy: KtFile,
        targetDirectory: PsiDirectory,
        targetFileName: String,
        isSingleDeclarationInFile: Boolean
    ): RefactoringResult {
        val targetFile = runWriteAction { // implicit package prefix may change after copy
            val targetDirectoryFqName = targetDirectory.getFqNameWithImplicitPrefix()
            val copiedFile = targetDirectory.copyFileFrom(targetFileName, fileToCopy)

            if (copiedFile is KtFile) {
                //set the package statement first to ensure
                // that the usages from the old package would be explicitly imported by shortenReferences of [retargetInternalUsagesForCopyFile]
                if (fileToCopy.packageMatchesDirectoryOrImplicit()) {
                    targetDirectoryFqName?.quoteIfNeeded()?.let { copiedFile.packageFqName = it }
                }
                retargetInternalUsagesForCopyFile(fileToCopy, copiedFile)
            }
            copiedFile
        }

        val copiedDeclaration = if (isSingleDeclarationInFile && targetFile is KtFile) {
            targetFile.declarations.singleOrNull() as? KtNamedDeclaration
        } else null

        return RefactoringResult(targetFile, copiedDeclaration)
    }

    private fun doRefactoringOnElement(
        sourceData: SourceData, targetFile: KtFile
    ): RefactoringResult {
        val restoredInternalUsages = ArrayList<UsageInfo>()
        val oldToNewElementsMapping = HashMap<KtNamedDeclaration, KtNamedDeclaration>()

        val copiedDeclaration = runWriteAction {
            val newElements = sourceData.elementsToCopy.map { targetFile.add(it.copy()) as KtNamedDeclaration }

            sourceData.elementsToCopy.zip(newElements).forEach { (original, newElement) ->
                original.collectDescendantsOfType<KtNamedDeclaration>()
                    .zip(newElement.collectDescendantsOfType<KtNamedDeclaration>())
                    .toMap(oldToNewElementsMapping)
            }

            @Suppress("UNCHECKED_CAST")
            retargetInternalUsages(oldToNewElementsMapping as Map<PsiElement, PsiElement>, fromCopy = true)

            newElements.singleOrNull()
        }

        return RefactoringResult(targetFile, copiedDeclaration, restoredInternalUsages)
    }

    private fun collectConflicts(
        sourceData: SourceData,
        targetData: TargetData,
    ): MultiMap<PsiElement, String> {

        if (isUnitTestMode() && BaseRefactoringProcessor.ConflictsInTestsException.isTestIgnore()) return MultiMap.empty()

        if (sourceData.project != sourceData.originalFile.project) return MultiMap.empty()

        val elements = mutableSetOf<KtNamedDeclaration>()
        elements.addAll(sourceData.elementsToCopy)
        (sourceData.singleElementToCopy as? KtFile)?.declarations?.filterIsInstance<KtNamedDeclaration>()?.forEach(elements::add)

        if (elements.isEmpty()) return MultiMap.empty()

        val (fakeTarget, _) = createCopyTarget(
            elements, targetData.targetDirWrapper.baseDirectory, targetData.targetDirWrapper.pkgName, targetData.newName
        )

        return MultiMap<PsiElement, String>().apply {
            putAllValues(checkVisibilityConflictsForInternalUsages(elements, fakeTarget))
            putAllValues(checkModuleDependencyConflictsForInternalUsages(elements, fakeTarget))
        }
    }

    override fun doClone(element: PsiElement) {}
}
