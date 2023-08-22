// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.psi.KtFile

class AfterConversionPass(val project: Project, private val postProcessor: PostProcessor) {
    @JvmOverloads
    fun run(
        kotlinFile: KtFile,
        converterContext: ConverterContext?,
        range: TextRange?,
        onPhaseChanged: ((Int, String) -> Unit)? = null
    ) {
        postProcessor.doAdditionalProcessing(
            when {
                range != null -> JKPieceOfCodePostProcessingTarget(kotlinFile, range.toRangeMarker(kotlinFile))
                else -> JKMultipleFilesPostProcessingTarget(listOf(kotlinFile))
            },
            converterContext,
            onPhaseChanged
        )
    }
}

fun TextRange.toRangeMarker(file: KtFile): RangeMarker =
    runReadAction { file.viewProvider.document!!.createRangeMarker(startOffset, endOffset) }.apply {
        isGreedyToLeft = true
        isGreedyToRight = true
    }