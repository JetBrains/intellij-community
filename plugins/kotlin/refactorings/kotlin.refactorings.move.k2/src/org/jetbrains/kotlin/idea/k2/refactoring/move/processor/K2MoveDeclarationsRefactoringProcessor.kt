// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesDialog
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.psi.deleteSingle
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFileAnnotationList
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtPackageDirective

class K2MoveDeclarationsRefactoringProcessor(
    private val operationDescriptor: K2MoveOperationDescriptor.Declarations
) : BaseRefactoringProcessor(operationDescriptor.project) {
    override fun getCommandName(): String = KotlinBundle.message("command.move.declarations")

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor = operationDescriptor.usageViewDescriptor()

    override fun findUsages(): Array<UsageInfo> {
        if (!operationDescriptor.searchReferences) return emptyArray()
        return operationDescriptor.moveDescriptors.flatMap { moveDescriptor ->
            moveDescriptor.source.elements.flatMap { elem ->
                elem.findUsages(operationDescriptor.searchInComments, operationDescriptor.searchForText, moveDescriptor.target.pkgName)
            }
        }.toTypedArray()
    }

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val usages = refUsages.get()
        val conflicts = ActionUtil.underModalProgress(
            operationDescriptor.project,
            RefactoringBundle.message("detecting.possible.conflicts")
        ) {
            MultiMap<PsiElement, String>().apply {
                operationDescriptor.moveDescriptors.forEach { moveDescriptor ->
                    putAllValues(
                        findAllMoveConflicts(
                            topLevelDeclarationsToMove = moveDescriptor.source.elements,
                            allDeclarationsToMove = operationDescriptor.sourceElements,
                            targetDir = moveDescriptor.target.baseDirectory,
                            targetPkg = moveDescriptor.target.pkgName,
                            usages = usages
                                .filterIsInstance<MoveRenameUsageInfo>()
                                .filter { it.referencedElement in moveDescriptor.source.elements },
                        )
                    )
                }
            }
        }
        return showConflicts(conflicts, usages)
    }

    /**
     * We consider a file as effectively empty (which implies it can be safely deleted)
     * if it contains only package, import and file annotation statements.
     */
    private fun KtFile.isEffectivelyEmpty(): Boolean {
        if (!declarations.isEmpty()) return false
        return children.none {
            it !is PsiWhiteSpace && it !is KtImportList && it !is KtPackageDirective && it !is KtFileAnnotationList
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun performRefactoring(usages: Array<out UsageInfo>) {
        val movedElements = allowAnalysisOnEdt {
            operationDescriptor.moveDescriptors.flatMap { moveDescriptor ->
                val elementsToMove = moveDescriptor.source.elements.withContext()
                val targetFile = moveDescriptor.target.getOrCreateTarget(operationDescriptor.dirStructureMatchesPkg)
                val sourceFiles = elementsToMove.map { it.containingFile as KtFile }.distinct()
                val oldToNewMap = elementsToMove.moveInto(targetFile)
                moveDescriptor.source.elements.forEach(PsiElement::deleteSingle)
                // Delete files if they are effectively empty after moving declarations out of them
                sourceFiles.filter { it.isEffectivelyEmpty() }.forEach { it.delete() }
                @Suppress("UNCHECKED_CAST")
                retargetUsagesAfterMove(usages.toList(), oldToNewMap as Map<PsiElement, PsiElement>)
                oldToNewMap.values
            }
        }

        if (MoveFilesOrDirectoriesDialog.isOpenInEditorProperty()) { // for simplicity, we re-use logic from move files
            ApplicationManager.getApplication().invokeLater {
                EditorHelper.openFilesInEditor(movedElements.toTypedArray())
            }
        }
    }

    override fun performPsiSpoilingRefactoring() {
        operationDescriptor.moveCallBack?.refactoringCompleted()
    }

    override fun getBeforeData(): RefactoringEventData = RefactoringEventData().apply {
        addElements(operationDescriptor.sourceElements)
    }
}