// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.asTextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import org.jetbrains.kotlin.j2k.PostProcessingTarget.MultipleFilesPostProcessingTarget
import org.jetbrains.kotlin.j2k.PostProcessingTarget.PieceOfCodePostProcessingTarget
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange

interface PostProcessing {
    val options: PostProcessingOptions
        get() = PostProcessingOptions.DEFAULT

    fun runProcessing(target: PostProcessingTarget, converterContext: NewJ2kConverterContext)
}

fun PostProcessing.runProcessingConsideringOptions(target: PostProcessingTarget, converterContext: NewJ2kConverterContext) {
    if (options.disablePostprocessingFormatting) {
        PostprocessReformattingAspect.getInstance(converterContext.project).disablePostprocessFormattingInside {
            runProcessing(target, converterContext)
        }
    } else {
        runProcessing(target, converterContext)
    }
}

abstract class FileBasedPostProcessing : PostProcessing {
    final override fun runProcessing(target: PostProcessingTarget, converterContext: NewJ2kConverterContext) {
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

    abstract fun runProcessing(file: KtFile, allFiles: List<KtFile>, rangeMarker: RangeMarker?, converterContext: NewJ2kConverterContext)
}

abstract class ElementsBasedPostProcessing : PostProcessing {
    final override fun runProcessing(target: PostProcessingTarget, converterContext: NewJ2kConverterContext) {
        runProcessing(target.elements(), converterContext)
    }

    abstract fun runProcessing(elements: List<PsiElement>, converterContext: NewJ2kConverterContext)
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