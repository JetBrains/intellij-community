// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.listeners.RefactoringEventData
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
                            declarationsToCheck = moveDescriptor.source.elements,
                            allDeclarationsToMove = operationDescriptor.sourceElements,
                            targetDir = moveDescriptor.target.baseDirectory,
                            targetPkg = moveDescriptor.target.pkgName,
                            targetFileName = moveDescriptor.target.fileName,
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

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun performRefactoring(usages: Array<out UsageInfo>) {
        allowAnalysisOnEdt {
            operationDescriptor.moveDescriptors.forEach { moveDescriptor ->
                val elementsToMove = moveDescriptor.source.elements.withContext()
                val targetFile = moveDescriptor.target.getOrCreateTarget(operationDescriptor.dirStructureMatchesPkg)
                val sourceFiles = elementsToMove.map { it.containingFile as KtFile }.distinct()
                val oldToNewMap = elementsToMove.moveInto(targetFile)
                moveDescriptor.source.elements.forEach(PsiElement::deleteSingle)
                @Suppress("UNCHECKED_CAST")
                retargetUsagesAfterMove(usages.toList(), oldToNewMap as Map<PsiElement, PsiElement>)
                for (sourceFile in sourceFiles) {
                    if (sourceFile.declarations.isEmpty()) sourceFile.delete()
                }
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