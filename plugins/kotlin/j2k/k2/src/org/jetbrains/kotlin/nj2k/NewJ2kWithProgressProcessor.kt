// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager

class WithProgressProcessor(
    private val progressIndicator: ProgressIndicator?,
    private val phasesCount: Int
) {

    init {
        progressIndicator?.isIndeterminate = false
    }

    fun updateState(phase: Int, description: String) {
        ProgressManager.checkCanceled()
        progressIndicator?.checkCanceled()

        progressIndicator?.fraction = (phase + 1) * (1.0 / phasesCount.toDouble())
        progressIndicator?.text = KotlinJ2KK2Bundle.message("progress.text", description, phase + 1, phasesCount)
        progressIndicator?.text2 = ""
    }
}