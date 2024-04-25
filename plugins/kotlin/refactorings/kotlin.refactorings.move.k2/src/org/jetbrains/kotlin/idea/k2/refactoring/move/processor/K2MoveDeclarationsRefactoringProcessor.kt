// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.psi.deleteSingle
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveRenameUsageInfo.Companion.unMarkNonUpdatableUsages

/**
 * K2 move refactoring processor that moves declarations inside a new target according to the passed [descriptor]. The main difference
 * between this refactoring processor and [K2MoveFilesOrDirectoriesRefactoringProcessor] is that this processor moves declarations
 * individually and regenerates the imports that are required for the move. While in [K2MoveFilesOrDirectoriesRefactoringProcessor] the
 * whole file, including imports is moved, then imports are adjusted when necessary.
 */
class K2MoveDeclarationsRefactoringProcessor(val descriptor: K2MoveDescriptor.Declarations) : BaseRefactoringProcessor(descriptor.project) {
    override fun getCommandName(): String = KotlinBundle.message("command.move.declarations")

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor = descriptor.usageViewDescriptor()

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val usages = refUsages.get()
        val conflicts = findAllMoveConflicts(
            declarationsToCheck = descriptor.source.elements,
            allDeclarationsToMove = descriptor.source.elements,
            targetDir = descriptor.target.baseDirectory,
            targetPkg = descriptor.target.pkgName,
            targetFileName = descriptor.target.fileName,
            usages = usages.filterIsInstance<MoveRenameUsageInfo>()
        )
        return showConflicts(conflicts, usages)
    }

    override fun findUsages(): Array<UsageInfo> {
        if (!descriptor.searchReferences) return emptyArray()
        return descriptor.source.elements.flatMap {
            it.findUsages(descriptor.searchInComments, descriptor.searchForText, descriptor.target.pkgName)
        }.toTypedArray()
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun performRefactoring(usages: Array<out UsageInfo>) = allowAnalysisOnEdt {
        val elementsToMove = descriptor.source.elements
        unMarkNonUpdatableUsages(elementsToMove)
        val targetFile = descriptor.target.getOrCreateTarget()
        val sourceFiles = elementsToMove.map { it.containingKtFile }.distinct()
        val oldToNewMap = elementsToMove.moveInto(targetFile)
        descriptor.source.elements.forEach(PsiElement::deleteSingle)
        retargetUsagesAfterMove(usages.toList(), oldToNewMap)
        for (sourceFile in sourceFiles) {
            if (sourceFile.declarations.isEmpty()) sourceFile.delete()
        }
    }

    override fun getBeforeData(): RefactoringEventData = RefactoringEventData().apply {
        addElements(descriptor.source.elements)
    }
}