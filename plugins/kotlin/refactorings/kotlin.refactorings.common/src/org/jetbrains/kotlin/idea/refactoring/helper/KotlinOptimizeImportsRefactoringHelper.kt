// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.helper

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.RefactoringHelper
import com.intellij.usageView.UsageInfo
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinOptimizeImportsFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer

// Based on com.intellij.refactoring.OptimizeImportsRefactoringHelper
class KotlinOptimizeImportsRefactoringHelper : RefactoringHelper<Set<KtFile>> {
    internal open class CollectUnusedImportsTask(
        project: Project,
        private val unusedImports: MutableSet<SmartPsiElementPointer<KtImportDirective>>,
        private val operationData: Set<KtFile>
    ) : Task.Modal(project, KotlinBundle.message("optimize.imports.collect.unused.imports"), true) {

        override fun run(indicator: ProgressIndicator) {
            run {
                indicator.isIndeterminate = false

                val myTotalCount = operationData.size
                for ((counter, file) in operationData.withIndex()) {
                    ReadAction.nonBlocking<Unit> {
                        val virtualFile = file.virtualFile ?: return@nonBlocking

                        indicator.fraction = counter.toDouble() / myTotalCount
                        indicator.text2 = virtualFile.presentableUrl

                        val importData = KotlinOptimizeImportsFacility.getInstance().analyzeImports(file)
                        importData?.unusedImports?.mapTo(unusedImports) { it.createSmartPointer() }
                    }
                        .inSmartMode(project)
                        .wrapProgress(indicator)
                        .expireWhen { !file.isValid || project.isDisposed() }
                        .executeSynchronously()
                }
            }
        }
    }

    internal class OptimizeImportsTask(
        project: Project,
        private val pointers: Set<SmartPsiElementPointer<KtImportDirective>>
    ) : Task.Modal(project, KotlinBundle.message("optimize.imports.task.removing.redundant.imports"), false) {
        override fun run(indicator: ProgressIndicator) {
            indicator.isIndeterminate = false

            val myTotal: Int = pointers.size
            for ((counter, pointer) in pointers.withIndex()) {
                indicator.fraction = counter.toDouble() / myTotal

                runReadAction { PsiUtilCore.getVirtualFile(pointer.element) }?.let { virtualFile ->
                    val presentableUrl = virtualFile.presentableUrl
                    indicator.text2 = presentableUrl
                    ApplicationManager.getApplication().invokeAndWait {
                        project.executeWriteCommand(KotlinBundle.message("delete.0", presentableUrl)) {
                            try {
                                pointer.element?.delete()
                            } catch (e: IncorrectOperationException) {
                                LOG.error(e)
                            }
                        }
                    }
                }
            }
        }

        companion object {
            private val LOG = Logger.getInstance("#" + OptimizeImportsTask::class.java.name)
        }
    }

    private fun prepareOperation(usages: Array<UsageInfo>): Set<KtFile> = usages.mapNotNullTo(LinkedHashSet()) {
        if (!it.isNonCodeUsage) it.file as? KtFile else null
    }

    override fun prepareOperation(usages: Array<UsageInfo>, elements: List<PsiElement>): Set<KtFile> {
        return elements.mapNotNull { it.containingFile as? KtFile }.toSet() + prepareOperation(usages)
    }

    override fun performOperation(project: Project, operationData: Set<KtFile>) {
        if (operationData.isEmpty()) return

        CodeStyleManager.getInstance(project).performActionWithFormatterDisabled {
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }

        val unusedImports = mutableSetOf<SmartPsiElementPointer<KtImportDirective>>()

        val progressManager = ProgressManager.getInstance()

        val collectTask = object : CollectUnusedImportsTask(project, unusedImports, operationData) {
            override fun onSuccess() {
                val progressTask = OptimizeImportsTask(project, unusedImports)
                progressManager.run(progressTask)
            }
        }

        progressManager.run(collectTask)
    }
}