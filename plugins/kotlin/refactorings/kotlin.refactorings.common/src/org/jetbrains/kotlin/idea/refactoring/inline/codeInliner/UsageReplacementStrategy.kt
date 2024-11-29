// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.inline.codeInliner

import com.intellij.codeInsight.FileModificationService
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.ModalityUiUtil
import kotlinx.coroutines.Runnable
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.references.KtSimpleReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

private val LOG = Logger.getInstance(UsageReplacementStrategy::class.java)

interface UsageReplacementStrategy {
    fun createReplacer(usage: KtReferenceExpression): (() -> KtElement?)?

    companion object {
        val KEY = Key<Unit>("UsageReplacementStrategy.replaceUsages")
    }
}

fun UsageReplacementStrategy.replaceUsagesInWholeProject(
    targetPsiElement: PsiElement,
    @NlsContexts.DialogTitle progressTitle: String,
    @NlsContexts.Command commandName: String,
    unwrapSpecialUsages: Boolean = true,
    unwrapper: (KtReferenceExpression) -> KtSimpleNameExpression?
) {
    val project = targetPsiElement.project
    val usages = runWithModalProgressBlocking(project, progressTitle) {
        runReadAction {
            val searchScope = KotlinSourceFilterScope.projectSources(GlobalSearchScope.projectScope(project), project)
            ReferencesSearch.search(targetPsiElement, searchScope)
                .filterIsInstance<KtSimpleReference<KtReferenceExpression>>()
                .map { ref -> ref.expression }
        }
    }

    val files = runReadAction { usages.map { it.containingFile.virtualFile }.distinct() }
    if (!FileModificationService.getInstance().prepareVirtualFilesForWrite(project, files)) return

    project.executeWriteCommand(commandName) {
        replaceUsages(usages, unwrapSpecialUsages, unwrapper)
    }
}

fun UsageReplacementStrategy.replaceUsages(
    usages: Collection<KtReferenceExpression>,
    unwrapSpecialUsages: Boolean = true,
    unwrapSpecialUsageOrNull: (KtReferenceExpression) -> KtSimpleNameExpression?,
) {
    val usagesByFile = usages.groupBy { it.containingFile }

    for ((file, usagesInFile) in usagesByFile) {
        usagesInFile.forEach { it.putCopyableUserData(UsageReplacementStrategy.KEY, Unit) }

        // we should delete imports later to not affect other usages
        val importsToDelete = mutableListOf<KtImportDirective>()

        var usagesToProcess = usagesInFile
        while (usagesToProcess.isNotEmpty()) {
            if (processUsages(usagesToProcess, importsToDelete, unwrapSpecialUsages, unwrapSpecialUsageOrNull)) break

            // some usages may get invalidated we need to find them in the tree
            usagesToProcess = file.collectDescendantsOfType { it.getCopyableUserData(UsageReplacementStrategy.KEY) != null }
        }

        file.forEachDescendantOfType<KtSimpleNameExpression> { it.putCopyableUserData(UsageReplacementStrategy.KEY, null) }

        if (importsToDelete.isEmpty()) continue

        importsToDelete.forEach { it.delete() }
        val importList = (file as? KtFile)?.importList
        if (importList != null && importList.imports.isEmpty()) {
            val newList = importList.copy()
            importList.delete()
            file.addAfter(newList, file.packageDirective)
        }
    }
}

/**
 * @return false if some usages were invalidated
 */
private fun UsageReplacementStrategy.processUsages(
    usages: List<KtReferenceExpression>,
    importsToDelete: MutableList<KtImportDirective>,
    unwrapSpecialUsages: Boolean,
    unwrapSpecialUsageOrNull: (KtReferenceExpression) -> KtSimpleNameExpression?,
): Boolean {
    val sortedUsages = usages.sortedWith { element1, element2 ->
        if (element1.parent.textRange.intersects(element2.parent.textRange)) {
            compareValuesBy(element2, element1) { it.startOffset }
        } else {
            compareValuesBy(element1, element2) { it.startOffset }
        }
    }

    var invalidUsagesFound = false
    for (usage in sortedUsages) {
        try {
            if (!usage.isValid) {
                invalidUsagesFound = true
                continue
            }

            if (unwrapSpecialUsages) {
                val specialUsage = unwrapSpecialUsageOrNull(usage)
                if (specialUsage != null) {
                    createReplacer(specialUsage)?.invoke()
                    continue
                }
            }

            //TODO: keep the import if we don't know how to replace some of the usages
            val importDirective = usage.getStrictParentOfType<KtImportDirective>()
            if (importDirective != null) {
                if (!importDirective.isAllUnder) {
                    val nameExpression = importDirective.importedReference?.getQualifiedElementSelector() as? KtSimpleNameExpression
                    if (nameExpression?.mainReference?.multiResolve(false)?.size == 1) {
                        importsToDelete.add(importDirective)
                    }
                }
                continue
            }

            val element = createReplacer(usage)?.invoke()
            element?.parent?.reformatted(true)
        } catch (e: Throwable) {
            if (e is ControlFlowException) throw e
            LOG.error(e)
        }
    }
    return !invalidUsagesFound
}
