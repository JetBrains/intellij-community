// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.asTextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import org.jetbrains.kotlin.j2k.PostProcessingTarget.MultipleFilesPostProcessingTarget
import org.jetbrains.kotlin.j2k.PostProcessingTarget.PieceOfCodePostProcessingTarget
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange

interface PostProcessing {
    val options: PostProcessingOptions
        get() = PostProcessingOptions.DEFAULT

    // For K1: analysis and application are mixed between post-processings
    fun runProcessing(target: PostProcessingTarget, converterContext: ConverterContext)

    // For K2: separate analysis stage and application stage
    // to avoid reanalyzing the changed files
    fun computeAppliers(target: PostProcessingTarget, converterContext: ConverterContext): List<PostProcessingApplier>
}

fun PostProcessing.runProcessingConsideringOptions(target: PostProcessingTarget, converterContext: ConverterContext) {
    if (options.disablePostprocessingFormatting) {
        PostprocessReformattingAspect.getInstance(converterContext.project).disablePostprocessFormattingInside {
            runProcessing(target, converterContext)
        }
    } else {
        runProcessing(target, converterContext)
    }
}

abstract class FileBasedPostProcessing : PostProcessing {
    final override fun runProcessing(target: PostProcessingTarget, converterContext: ConverterContext) {
        when (target) {
            is PieceOfCodePostProcessingTarget ->
                runProcessing(target.file, listOf(target.file), target.rangeMarker, converterContext)

            is MultipleFilesPostProcessingTarget -> {
                for (file in target.files) {
                    runProcessing(file, target.files, rangeMarker = null, converterContext)
                }
            }
        }
    }

    abstract fun runProcessing(file: KtFile, allFiles: List<KtFile>, rangeMarker: RangeMarker?, converterContext: ConverterContext)

    final override fun computeAppliers(
        target: PostProcessingTarget,
        converterContext: ConverterContext
    ): List<PostProcessingApplier> = when (target) {
        is PieceOfCodePostProcessingTarget ->
            listOf(computeApplier(target.file, listOf(target.file), target.rangeMarker, converterContext))

        is MultipleFilesPostProcessingTarget -> {
            target.files.map { file ->
                computeApplier(file, target.files, rangeMarker = null, converterContext)
            }
        }
    }

    abstract fun computeApplier(
        file: KtFile,
        allFiles: List<KtFile>,
        rangeMarker: RangeMarker?,
        converterContext: ConverterContext
    ): PostProcessingApplier
}

abstract class ElementsBasedPostProcessing : PostProcessing {
    final override fun runProcessing(target: PostProcessingTarget, converterContext: ConverterContext) {
        runProcessing(target.elements(), converterContext)
    }

    abstract fun runProcessing(elements: List<PsiElement>, converterContext: ConverterContext)

    final override fun computeAppliers(
        target: PostProcessingTarget,
        converterContext: ConverterContext
    ): List<PostProcessingApplier> = listOf(computeApplier(target.elements(), converterContext))

    abstract fun computeApplier(elements: List<PsiElement>, converterContext: ConverterContext): PostProcessingApplier
}

data class NamedPostProcessingGroup(val description: String, val processings: List<PostProcessing>)

data class PostProcessingOptions(val disablePostprocessingFormatting: Boolean = true) {
    companion object {
        val DEFAULT = PostProcessingOptions()
    }
}

sealed class PostProcessingTarget {
    data class PieceOfCodePostProcessingTarget(val file: KtFile, val rangeMarker: RangeMarker) : PostProcessingTarget()
    data class MultipleFilesPostProcessingTarget(val files: List<KtFile>) : PostProcessingTarget()
}

fun PostProcessingTarget.elements(): List<PsiElement> = when (this) {
    is PieceOfCodePostProcessingTarget -> runReadAction {
        val range = rangeMarker.asTextRange ?: return@runReadAction emptyList()
        file.elementsInRange(range)
    }

    is MultipleFilesPostProcessingTarget -> files
}

fun PostProcessingTarget.files(): List<KtFile> = when (this) {
    is PieceOfCodePostProcessingTarget -> listOf(file)
    is MultipleFilesPostProcessingTarget -> files
}

interface PostProcessingApplier {
    fun apply()
}