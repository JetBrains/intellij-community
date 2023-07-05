// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.ide.IdeDeprecatedMessagesBundle
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.move.MoveMultipleElementsViewDescriptor
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.psi.deleteSingle
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

class K2MoveMembersRefactoringProcessor(
    val descriptor: K2MoveDescriptor.Members
) : BaseRefactoringProcessor(descriptor.target.pkg.project) {
    override fun getCommandName(): String = KotlinBundle.message("command.move.declarations")

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
        val targetContainerFqName = descriptor.target.pkg.qualifiedName.let { if (it == "") IdeDeprecatedMessagesBundle.message("default.package.presentable.name") else it }
        return MoveMultipleElementsViewDescriptor(descriptor.source.elements.toTypedArray(), targetContainerFqName)
    }

    override fun findUsages(): Array<UsageInfo> {
        return descriptor.source.elements.flatMap { member ->
            val references = ReferencesSearch.search(member).findAll().filter { reference ->
                reference.element.getNonStrictParentOfType<KtImportDirective>() == null
            }
            references.map { reference -> MoveRenameUsageInfo(reference, member) }
        }.toTypedArray()
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun performRefactoring(usages: Array<out UsageInfo>) {
        val targetFile = descriptor.target.file

        val usageMap = usages.groupBy {
            (it as MoveRenameUsageInfo).referencedElement as KtNamedDeclaration
        }

        val sourceFiles = descriptor.source.elements.map { it.containingKtFile }.distinct()

        for (declaration in descriptor.source.elements) {
            val newElement = targetFile.add(declaration) as KtNamedDeclaration
            usageMap[declaration]?.forEach { usage ->
                val renameUsage = usage as MoveRenameUsageInfo
                allowAnalysisOnEdt { renameUsage.reference?.bindToElement(newElement) }
            }
            declaration.deleteSingle() // can't use delete here because calling it on single element top level classes wil remove the file
        }

        if (descriptor.deleteEmptySourceFiles) {
            for (sourceFile in sourceFiles) {
                if (sourceFile.declarations.isEmpty()) sourceFile.delete()
            }
        }
    }
}