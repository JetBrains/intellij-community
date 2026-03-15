// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.k2.scratch

import com.intellij.ide.scratch.ScratchFileCreationHelper
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.analyzeCopy
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.script.v1.ScriptRelatedModuleNameFile
import org.jetbrains.kotlin.idea.k2.codeinsight.copyPaste.KotlinReferenceRestoringHelper
import org.jetbrains.kotlin.idea.refactoring.createTempCopy
import org.jetbrains.kotlin.idea.statistics.KotlinCreateFileFUSCollector
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.KtFile

class KotlinK2ScratchFileCreationHelper : ScratchFileCreationHelper() {

    override fun prepareText(project: Project, context: Context, dataContext: DataContext): Boolean {
        KotlinCreateFileFUSCollector.logFileTemplate("Kotlin Scratch")
        context.fileExtension = KotlinParserDefinition.STD_SCRIPT_SUFFIX

        return true
    }

    override fun beforeCreate(
        project: Project,
        context: Context
    ) {
        KotlinCreateFileFUSCollector.logFileTemplate("Kotlin Scratch From Selection")
        context.fileExtension = KotlinParserDefinition.STD_SCRIPT_SUFFIX
    }

    override fun afterCreate(
        project: Project,
        context: Context,
        scratchFile: PsiFile
    ) {
        val sourceKtFile = context.sourceFile as? KtFile ?: return
        ScriptRelatedModuleNameFile[project, scratchFile.virtualFile] = ModuleUtilCore.findModuleForFile(sourceKtFile)?.name

        val sourceFileCopy = sourceKtFile.createTempCopy(sourceKtFile.text)
        project.service<KotlinScratchCoroutineScopeService>().scope.launch {
            withBackgroundProgress(project, KotlinBundle.message("copy.paste.resolve.pasted.references"), cancellable = true) {
                addImports(project, context, scratchFile as KtFile, sourceFileCopy)
            }
        }
    }

    private suspend fun addImports(project: Project, context: Context, scratchFile: KtFile, sourceFileCopy: KtFile) {
        val selectionRange = context.selectionRange ?: return

        val targetReferencesToRestore = readAction {
            calculateTargetReferencesToRestore(sourceFileCopy, selectionRange, scratchFile)
        }

        if (targetReferencesToRestore.isEmpty()) return

        checkCanceled()

        writeAction {
            CommandProcessor.getInstance().executeCommand(project, {
                for (ref in targetReferencesToRestore) {
                    KotlinReferenceRestoringHelper.restoreReference(scratchFile, ref)
                }
                scratchFile.importList?.let { CodeStyleManager.getInstance(project).reformat(it) }
            }, KotlinBundle.message("copy.paste.restore.pasted.references.capitalized"), null)
        }
    }

    private fun calculateTargetReferencesToRestore(
        sourceFileCopy: KtFile,
        selectionRange: TextRange,
        scratchFile: KtFile
    ): List<KotlinReferenceRestoringHelper.ReferenceToRestore> {
        val sourceReferenceInfos = KotlinReferenceRestoringHelper.collectSourceReferenceInfos(
            sourceFileCopy,
            intArrayOf(selectionRange.startOffset),
            intArrayOf(selectionRange.endOffset)
        )

        if (sourceReferenceInfos.isEmpty()) return emptyList()

        val sourceReferencesInScratch = KotlinReferenceRestoringHelper.findSourceReferencesInTargetFile(
            sourceFileCopy, sourceReferenceInfos, scratchFile, targetOffset = 0
        )

        val resolvedSourceReferences = analyzeCopy(sourceFileCopy, KaDanglingFileResolutionMode.PREFER_SELF) {
            KotlinReferenceRestoringHelper.getResolvedSourceReferencesThatMightRequireRestoring(
                sourceFileCopy, sourceReferenceInfos, listOf(selectionRange)
            )
        }

        return analyze(scratchFile) {
            KotlinReferenceRestoringHelper.getTargetReferencesToRestore(sourceReferencesInScratch, resolvedSourceReferences)
        }
    }
}

@Service(Service.Level.PROJECT)
class KotlinScratchCoroutineScopeService(val scope: CoroutineScope)

