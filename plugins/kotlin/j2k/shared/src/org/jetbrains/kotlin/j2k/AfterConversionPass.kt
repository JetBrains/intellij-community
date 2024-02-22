// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.j2k.PostProcessingTarget.MultipleFilesPostProcessingTarget
import org.jetbrains.kotlin.j2k.PostProcessingTarget.PieceOfCodePostProcessingTarget
import org.jetbrains.kotlin.psi.KtFile

class AfterConversionPass(val project: Project, private val postProcessor: PostProcessor) {
    @JvmOverloads
    fun run(
        kotlinFile: KtFile,
        converterContext: ConverterContext?,
        range: TextRange?,
        onPhaseChanged: ((Int, String) -> Unit)? = null
    ) {
        val target =
            if (range != null) PieceOfCodePostProcessingTarget(kotlinFile, range.toRangeMarker(kotlinFile))
            else MultipleFilesPostProcessingTarget(listOf(kotlinFile))
        postProcessor.doAdditionalProcessing(target, converterContext, onPhaseChanged)
    }
}

private fun TextRange.toRangeMarker(file: KtFile): RangeMarker {
    val rangeMarker = runReadAction { file.viewProvider.document!!.createRangeMarker(startOffset, endOffset) }
    return rangeMarker.apply {
        isGreedyToLeft = true
        isGreedyToRight = true
    }
}