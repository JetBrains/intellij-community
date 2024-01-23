// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiJavaFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.j2k.WithProgressProcessor

@ApiStatus.Internal
class NewJ2kWithProgressProcessor(
    private val progress: ProgressIndicator?,
    private val files: List<PsiJavaFile>?,
    private val phasesCount: Int
) : WithProgressProcessor {
    companion object {
        val DEFAULT = NewJ2kWithProgressProcessor(null, null, 0)
    }

    init {
        progress?.isIndeterminate = false
    }

    override fun updateState(fileIndex: Int?, phase: Int, description: String) {
        if (fileIndex == null)
            updateState(phase, 1, 1, null, description)
        else
            updateState(phase, 0, 1, fileIndex, description)
    }

    override fun updateState(
        phase: Int,
        subPhase: Int,
        subPhaseCount: Int,
        fileIndex: Int?,
        description: String
    ) {
        ProgressManager.checkCanceled()
        progress?.checkCanceled()
        val singlePhaseFraction = 1.0 / phasesCount.toDouble()
        val singleSubPhaseFraction = singlePhaseFraction / subPhaseCount.toDouble()

        var resultFraction = phase * singlePhaseFraction + subPhase * singleSubPhaseFraction
        if (files != null && fileIndex != null && files.isNotEmpty()) {
            val fileFraction = singleSubPhaseFraction / files.size.toDouble()
            resultFraction += fileFraction * fileIndex
        }
        progress?.fraction = resultFraction

        if (subPhaseCount > 1) {
            progress?.text = KotlinNJ2KBundle.message(
                "subphase.progress.text",
                description,
                subPhase,
                subPhaseCount,
                phase + 1,
                phasesCount
            )
        } else {
            progress?.text = KotlinNJ2KBundle.message("progress.text", description, phase + 1, phasesCount)
        }
        progress?.text2 = when {
            !files.isNullOrEmpty() && fileIndex != null -> files[fileIndex].virtualFile.presentableUrl + if (files.size > 1) " ($fileIndex/${files.size})" else ""
            else -> ""
        }
    }

    override fun <TInputItem, TOutputItem> processItems(
        fractionPortion: Double,
        inputItems: Iterable<TInputItem>,
        processItem: (TInputItem) -> TOutputItem
    ): List<TOutputItem> {
        throw AbstractMethodError("Should not be called for new J2K")
    }

    override fun <T> process(action: () -> T): T = action()
}