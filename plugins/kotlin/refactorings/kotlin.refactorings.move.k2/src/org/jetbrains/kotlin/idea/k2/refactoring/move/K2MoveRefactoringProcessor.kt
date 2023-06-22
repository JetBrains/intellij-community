// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.ide.IdeDeprecatedMessagesBundle
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.move.MoveMultipleElementsViewDescriptor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.move.*
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class K2MoveRefactoringProcessor(
    private val descriptor: MoveDeclarationsDescriptor,
    private val mover: KotlinMover = KotlinMover.Default,
    private val throwOnConflicts: Boolean = false
) : BaseRefactoringProcessor(descriptor.project) {
    override fun getCommandName(): String = KotlinBundle.message("command.move.declarations")

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
        val targetContainerFqName = descriptor.moveTarget.targetContainerFqName?.let {
            if (it.isRoot) IdeDeprecatedMessagesBundle.message("default.package.presentable.name") else it.asString()
        } ?: IdeDeprecatedMessagesBundle.message("default.package.presentable.name")
        return MoveMultipleElementsViewDescriptor(descriptor.moveSource.elementsToMove.toTypedArray(), targetContainerFqName)
    }

    override fun findUsages(): Array<UsageInfo> {
        return emptyArray()
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        fun moveDeclaration(
            declaration: KtNamedDeclaration,
            moveTarget: KotlinMoveTarget,
            mover: KotlinMover,
            delegate: KotlinMoveDeclarationDelegate
        ): KtNamedDeclaration {
            val targetContainer = moveTarget.getOrCreateTargetPsi(declaration)
            delegate.preprocessDeclaration(moveTarget, declaration)
            return mover(declaration, targetContainer)
        }

        val sourceFiles = descriptor.moveSource.elementsToMove.map { it.containingKtFile }.distinct()

        for (declaration in descriptor.moveSource.elementsToMove) {
            moveDeclaration(declaration, descriptor.moveTarget, mover, descriptor.delegate)
        }

        if (descriptor.deleteSourceFiles) {
            for (sourceFile in sourceFiles) {
                if (sourceFile.declarations.isEmpty()) sourceFile.delete()
            }
        }
    }
}