// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiComment
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
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor.DeclarationsMoveDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveSourceDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringListener
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.startOffset

abstract class K2BaseMoveDeclarationsRefactoringProcessor<T : DeclarationsMoveDescriptor>(
    protected val operationDescriptor: T
) : BaseRefactoringProcessor(operationDescriptor.project) {
    companion object {
        const val REFACTORING_ID: String = "move.kotlin.declarations"
    }

    override fun getRefactoringId(): String = REFACTORING_ID

    override fun getCommandName(): String = KotlinBundle.message("command.move.declarations")

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor = operationDescriptor.usageViewDescriptor()

    protected open fun getUsages(moveDescriptor: K2MoveDescriptor): List<UsageInfo> {
        return moveDescriptor.source.elements
            .filterIsInstance<KtNamedDeclaration>()
            .flatMap { elem ->
                // We filter out constructors because calling bindTo on these references will break for light classes.
                if (elem is KtPrimaryConstructor || elem is KtSecondaryConstructor) return@flatMap emptyList()
                elem.findUsages(operationDescriptor.searchInComments, operationDescriptor.searchForText, moveDescriptor.target)
            }
    }

    protected open fun collectConflicts(moveDescriptor: K2MoveDescriptor, allUsages: MutableSet<UsageInfo>) {}

    override fun findUsages(): Array<UsageInfo> {
        if (!operationDescriptor.searchReferences) return emptyArray()
        val allUsages = operationDescriptor.moveDescriptors.flatMap { moveDescriptor ->
            val usages = operationDescriptor.moveDescriptors.flatMapTo(mutableSetOf(), ::getUsages)
            collectConflicts(moveDescriptor, usages)
            usages
        }.toTypedArray()

        return UsageViewUtil.removeDuplicatedUsages(allUsages)
    }

    /**
     * We consider a file as effectively empty (which implies it can be safely deleted)
     * if it contains only package, import and file annotation statements and comments before the
     * package directive (which are usually copyright notices).
     */
    private fun KtFile.isEffectivelyEmpty(): Boolean {
        if (!declarations.isEmpty()) return false
        val packageDeclaration = packageDirective
        return children.all {
            it is PsiWhiteSpace ||
                    it is KtImportList ||
                    it is KtPackageDirective ||
                    it is KtFileAnnotationList ||
                    // We do not consider comments before the package declarations as they are usually copyright notices.
                    (it is PsiComment && packageDeclaration != null && it.startOffset < packageDeclaration.startOffset)
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
     * Deletes the [element] from its file but also deletes the containing declaration or file
     * if it can be deleted and is
     */
    protected fun deleteMovedElement(element: KtNamedDeclaration) {
        val containingClass = element.containingClassOrObject
        element.deleteSingle()
        if (containingClass is KtObjectDeclaration && containingClass.isCompanion() && containingClass.isEffectivelyEmpty()) {
            containingClass.deleteSingle()
        }
    }

    protected val conflicts: MultiMap<PsiElement, String> = MultiMap<PsiElement, String>()

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
                        target = moveDescriptor.target,
                        usages = usages
                            .filterIsInstance<MoveRenameUsageInfo>()
                            .filter { it.referencedElement.willBeMoved(operationDescriptor.sourceElements) },
                    )
                )
            }
        }
        return showConflicts(conflicts, usages)
    }

    open fun preprocessUsages(project: Project, moveSource: K2MoveSourceDescriptor<*>, usages: List<UsageInfo>) {}
    open fun preprocessDeclaration(moveDescriptor: K2MoveDescriptor, originalDeclaration: KtNamedDeclaration) {}
    open fun postprocessDeclaration(moveTarget: K2MoveTargetDescriptor, originalDeclaration: PsiElement, newDeclaration: PsiElement) {}

    @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
    override fun performRefactoring(usages: Array<out UsageInfo>) {
        val movedElements = allowAnalysisOnEdt {
            allowAnalysisFromWriteAction {
                operationDescriptor.moveDescriptors.flatMap { moveDescriptor ->
                    preprocessUsages(moveDescriptor.project, moveDescriptor.source, usages.toList())

                    val declarationsToMove = moveDescriptor.source.elements
                    declarationsToMove.forEach { elementToMove ->
                        preprocessDeclaration(moveDescriptor, elementToMove)
                        operationDescriptor.preDeclarationMoved(elementToMove)
                    }

                    val elementsToMove = declarationsToMove.withContext()
                    val sourceFiles = elementsToMove.map { it.containingFile as KtFile }.distinct()
                    val oldToNewMap = moveDescriptor.target.addElementsToTarget(elementsToMove, operationDescriptor.dirStructureMatchesPkg)
                    moveDescriptor.source.elements.forEach(::deleteMovedElement)
                    // Delete files if they are effectively empty after moving declarations out of them
                    sourceFiles.filter { it.isEffectivelyEmpty() }.forEach { it.delete() }

                    @Suppress("UNCHECKED_CAST")
                    retargetUsagesAfterMove(usages.toList(), oldToNewMap as Map<PsiElement, PsiElement>)
                    oldToNewMap.forEach { original, new ->
                        postprocessDeclaration(moveDescriptor.target, original, new)

                        val originalDeclaration = original as? KtNamedDeclaration
                        val newDeclaration = new as? KtNamedDeclaration
                        if (originalDeclaration != null && newDeclaration != null) {
                            operationDescriptor.postDeclarationMoved(original, new)
                        }
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

    override fun doRun() {
        try {
            super.doRun()
        } finally {
            KotlinRefactoringListener.broadcastRefactoringExit(myProject, refactoringId)
        }
    }
}