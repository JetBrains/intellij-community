// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.ide.IdeDeprecatedMessagesBundle
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.move.MoveMultipleElementsViewDescriptor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.psi.deleteSingle
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor

class K2MoveMembersRefactoringProcessor(val descriptor: K2MoveDescriptor.Members) : BaseRefactoringProcessor(descriptor.project) {
    override fun getCommandName(): String = KotlinBundle.message("command.move.declarations")

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
        val targetContainerFqName = descriptor.target.pkgName.asString().let { if (it == "") IdeDeprecatedMessagesBundle.message(
          "default.package.presentable.name")
        else it }
        return MoveMultipleElementsViewDescriptor(descriptor.source.elements.toTypedArray(), targetContainerFqName)
    }

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val usages = refUsages.get()
        val conflicts = findAllMoveConflicts(descriptor)
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
      val targetFile = descriptor.target.getOrCreateFile()

      val sourceFiles = descriptor.source.elements.map { it.containingKtFile }.distinct()

      val oldToNewMap = descriptor.source.elements.associateWith { declaration ->
        targetFile.add(declaration)
      }.toMutableMap<PsiElement, PsiElement>()
      descriptor.source.elements.forEach(PsiElement::deleteSingle)

      retargetUsagesAfterMove(usages.toList(), oldToNewMap)

      for (sourceFile in sourceFiles) {
        if (sourceFile.declarations.isEmpty()) sourceFile.delete()
      }
    }
}