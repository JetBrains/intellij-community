// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.ide.IdeDeprecatedMessagesBundle
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.move.MoveMultipleElementsViewDescriptor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.psi.deleteSingle
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

class K2MoveMembersRefactoringProcessor(
    val descriptor: K2MoveDescriptor.Members
) : BaseRefactoringProcessor(descriptor.target.pkg.project) {
    override fun getCommandName(): String = KotlinBundle.message("command.move.declarations")

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
        val targetContainerFqName = descriptor.target.pkg.qualifiedName.let { if (it == "") IdeDeprecatedMessagesBundle.message("default.package.presentable.name") else it }
        return MoveMultipleElementsViewDescriptor(descriptor.source.elements.toTypedArray(), targetContainerFqName)
    }

    override fun findUsages(): Array<UsageInfo> {
        return if (descriptor.searchReferences) {
             descriptor.source.findusages(descriptor.searchInComments, descriptor.searchForText, descriptor.target.file.packageFqName)
                 .toTypedArray()
        } else emptyArray()
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun performRefactoring(usages: Array<out UsageInfo>) = allowAnalysisOnEdt {
        val targetFile = descriptor.target.file

        val sourceFiles = descriptor.source.elements.map { it.containingKtFile }.distinct()

        val oldToNewMap = descriptor.source.elements.associateWith {
            declaration -> targetFile.add(declaration)
        }.toMutableMap<PsiElement, PsiElement>()
        descriptor.source.elements.forEach(PsiElement::deleteSingle)

        retargetUsagesAfterMove(usages.toList(), oldToNewMap)

        for (sourceFile in sourceFiles) {
            if (sourceFile.declarations.isEmpty()) sourceFile.delete()
        }
    }
}