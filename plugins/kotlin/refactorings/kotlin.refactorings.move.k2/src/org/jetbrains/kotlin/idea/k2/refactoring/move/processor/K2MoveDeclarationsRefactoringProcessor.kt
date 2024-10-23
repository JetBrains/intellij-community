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
import com.intellij.usageView.UsageViewUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.psi.deleteSingle
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

class K2MoveDeclarationsRefactoringProcessor(
    private val operationDescriptor: K2MoveOperationDescriptor.Declarations
) : BaseRefactoringProcessor(operationDescriptor.project) {
    override fun getCommandName(): String = KotlinBundle.message("command.move.declarations")

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor = operationDescriptor.usageViewDescriptor()

    private val conflicts = MultiMap<PsiElement, String>()

    override fun findUsages(): Array<UsageInfo> {
        if (!operationDescriptor.searchReferences) return emptyArray()
        val allUsages = operationDescriptor.moveDescriptors.flatMapTo(mutableSetOf()) { moveDescriptor ->
            moveDescriptor.source.elements.flatMap { elem ->
                elem.findUsages(operationDescriptor.searchInComments, operationDescriptor.searchForText, moveDescriptor.target.pkgName)
            } + operationDescriptor.moveDeclarationsDelegate.findInternalUsages(moveDescriptor.source)
        }
        operationDescriptor.moveDescriptors.forEach { moveDescriptor ->
            operationDescriptor.moveDeclarationsDelegate.collectConflicts(
                moveDescriptor.target,
                allUsages,
                conflicts
            )
        }

        return UsageViewUtil.removeDuplicatedUsages(allUsages.toTypedArray())
    }

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val usages = refUsages.get()
        ActionUtil.underModalProgress(
            operationDescriptor.project,
            RefactoringBundle.message("detecting.possible.conflicts")
        ) {
            operationDescriptor.moveDescriptors.forEach { moveDescriptor ->
                conflicts.putAllValues(
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

    /**
     * We consider a class effectively empty if it only contains whitespaces.
     */
    private fun KtClassOrObject.isEffectivelyEmpty(): Boolean {
        if (!declarations.isEmpty()) return false
        return body?.children?.all { it is PsiWhiteSpace } == true
    }

    /**
     * Deletes
     */
    private fun deleteMovedElement(element: KtNamedDeclaration) {
        val containingClass = element.containingClassOrObject
        element.deleteSingle()
        if (containingClass is KtObjectDeclaration && containingClass.isCompanion() && containingClass.isEffectivelyEmpty()) {
            containingClass.deleteSingle()
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
    override fun performRefactoring(usages: Array<out UsageInfo>) {
        val movedElements = allowAnalysisOnEdt {
            allowAnalysisFromWriteAction {
                operationDescriptor.moveDescriptors.flatMap { moveDescriptor ->
                    operationDescriptor.moveDeclarationsDelegate.preprocessUsages(
                        moveDescriptor.project,
                        moveDescriptor.source,
                        usages.toList()
                    )

                    val elementsToMove = moveDescriptor.source.elements.withContext()
                    val targetFile = moveDescriptor.target.getOrCreateTarget(operationDescriptor.dirStructureMatchesPkg)
                    val sourceFiles = elementsToMove.map { it.containingFile as KtFile }.distinct()

                    elementsToMove.forEach { elementToMove ->
                        if (elementToMove !is KtNamedDeclaration) return@forEach
                        operationDescriptor.moveDeclarationsDelegate.preprocessDeclaration(moveDescriptor.target, elementToMove)
                    }

                    val oldToNewMap = elementsToMove.moveInto(targetFile)
                    moveDescriptor.source.elements.forEach(::deleteMovedElement)
                    // Delete files if they are effectively empty after moving declarations out of them
                    sourceFiles.filter { it.isEffectivelyEmpty() }.forEach { it.delete() }

                    @Suppress("UNCHECKED_CAST")
                    retargetUsagesAfterMove(usages.toList(), oldToNewMap as Map<PsiElement, PsiElement>)
                    oldToNewMap.forEach { original, new ->
                        operationDescriptor.moveDeclarationsDelegate.postprocessDeclaration(moveDescriptor.target, original, new)
                    }
                    oldToNewMap.values
                }
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