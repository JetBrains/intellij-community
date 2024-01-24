// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.refactoring.suggested.range
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.codeinsight.utils.commitAndUnblockDocument
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.j2k.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange

class NewJ2kPostProcessor : PostProcessor {
    companion object {
        private val LOG = Logger.getInstance("@org.jetbrains.kotlin.idea.j2k.post.processings.NewJ2kPostProcessor")
    }

    override fun insertImport(file: KtFile, fqName: FqName) {
        runUndoTransparentActionInEdt(inWriteAction = true) {
            val descriptors = file.resolveImportReference(fqName)
            descriptors.firstOrNull()?.let { ImportInsertHelper.getInstance(file.project).importDescriptor(file, it) }
        }
    }

    override val phasesCount = allProcessings.size

    override fun doAdditionalProcessing(
        target: JKPostProcessingTarget,
        converterContext: ConverterContext?,
        onPhaseChanged: ((Int, String) -> Unit)?
    ) {
        if (converterContext !is NewJ2kConverterContext) error("Invalid converter context for new J2K")

        for ((i, group) in allProcessings.withIndex()) {
            ProgressManager.checkCanceled()
            onPhaseChanged?.invoke(i, group.description)
            for (processing in group.processings) {
                ProgressManager.checkCanceled()
                try {
                    processing.runProcessingConsideringOptions(target, converterContext)
                    target.files().forEach(::commitFile)
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (t: Throwable) {
                    target.files().forEach(::commitFile)
                    LOG.error(t)
                }
            }
        }
    }

    private fun commitFile(file: KtFile) {
        runUndoTransparentActionInEdt(inWriteAction = true) {
            file.commitAndUnblockDocument()
        }
    }
}

internal interface GeneralPostProcessing {
    val options: PostProcessingOptions
        get() = PostProcessingOptions.DEFAULT

    fun runProcessing(target: JKPostProcessingTarget, converterContext: NewJ2kConverterContext)
}

private fun GeneralPostProcessing.runProcessingConsideringOptions(
    target: JKPostProcessingTarget,
    converterContext: NewJ2kConverterContext
) {
    if (options.disablePostprocessingFormatting) {
        PostprocessReformattingAspect.getInstance(converterContext.project).disablePostprocessFormattingInside {
            runProcessing(target, converterContext)
        }
    } else {
        runProcessing(target, converterContext)
    }
}

internal data class PostProcessingOptions(val disablePostprocessingFormatting: Boolean = true) {
    companion object {
        val DEFAULT = PostProcessingOptions()
    }
}

internal abstract class FileBasedPostProcessing : GeneralPostProcessing {
    final override fun runProcessing(target: JKPostProcessingTarget, converterContext: NewJ2kConverterContext) = when (target) {
        is JKPieceOfCodePostProcessingTarget ->
            runProcessing(target.file, listOf(target.file), target.rangeMarker, converterContext)

        is JKMultipleFilesPostProcessingTarget ->
            target.files.forEach { file ->
                runProcessing(file, target.files, rangeMarker = null, converterContext = converterContext)
            }
    }

    abstract fun runProcessing(file: KtFile, allFiles: List<KtFile>, rangeMarker: RangeMarker?, converterContext: NewJ2kConverterContext)
}

internal abstract class ElementsBasedPostProcessing : GeneralPostProcessing {
    final override fun runProcessing(target: JKPostProcessingTarget, converterContext: NewJ2kConverterContext) {
        runProcessing(target.elements(), converterContext)
    }

    abstract fun runProcessing(elements: List<PsiElement>, converterContext: NewJ2kConverterContext)
}

internal data class NamedPostProcessingGroup(val description: String, val processings: List<GeneralPostProcessing>)

fun JKPostProcessingTarget.elements(): List<PsiElement> = when (this) {
    is JKPieceOfCodePostProcessingTarget -> runReadAction {
        val range = rangeMarker.range ?: return@runReadAction emptyList()
        file.elementsInRange(range)
    }

    is JKMultipleFilesPostProcessingTarget -> files
}

private fun JKPostProcessingTarget.files(): List<KtFile> = when (this) {
    is JKPieceOfCodePostProcessingTarget -> listOf(file)
    is JKMultipleFilesPostProcessingTarget -> files
}