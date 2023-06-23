// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.ide.IdeDeprecatedMessagesBundle
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.move.MoveMultipleElementsViewDescriptor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

class K2MoveRefactoringProcessor(private val descriptor: K2MoveDescriptor) : BaseRefactoringProcessor(descriptor.project) {
    override fun getCommandName(): String = KotlinBundle.message("command.move.declarations")

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
        val targetContainerFqName = descriptor.target.pkg.qualifiedName.let { if (it == "") IdeDeprecatedMessagesBundle.message("default.package.presentable.name") else it }
        return MoveMultipleElementsViewDescriptor(descriptor.source.elementsToMove.toTypedArray(), targetContainerFqName)
    }

    override fun findUsages(): Array<UsageInfo> {
        return emptyArray()
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        if (descriptor.target !is K2MoveTarget.File) return // TODO support other targets

        val sourceFiles = descriptor.source.elementsToMove.map { it.containingKtFile }.distinct()

        val targetFile = descriptor.target.file
        for (declaration in descriptor.source.elementsToMove) {
            targetFile.add(declaration)
            declaration.delete()
        }

        if (descriptor.deleteEmptySourceFiles) {
            for (sourceFile in sourceFiles) {
                if (sourceFile.declarations.isEmpty()) sourceFile.delete()
            }
        }
    }
}