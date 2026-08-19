// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.asTextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.j2k.PostProcessingTarget.MultipleFilesPostProcessingTarget
import org.jetbrains.kotlin.j2k.PostProcessingTarget.PieceOfCodePostProcessingTarget
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange

interface PostProcessing {
    fun computeAppliers(target: PostProcessingTarget, converterContext: ConverterContext): List<PostProcessingApplier>
}

abstract class FileBasedPostProcessing : PostProcessing {
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
    final override fun computeAppliers(
        target: PostProcessingTarget,
        converterContext: ConverterContext
    ): List<PostProcessingApplier> = listOf(computeApplier(target.elements()))

    abstract fun computeApplier(elements: List<PsiElement>): PostProcessingApplier
}

data class NamedPostProcessingGroup(val description: String, val processings: List<PostProcessing>)

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