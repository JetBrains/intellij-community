// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiJavaFile
import org.jetbrains.annotations.Nls

class OldWithProgressProcessor(private val progress: ProgressIndicator?, private val files: List<PsiJavaFile>?) : WithProgressProcessor {
    companion object {
        val DEFAULT = OldWithProgressProcessor(null, null)
    }

    private val progressText
        @Suppress("DialogTitleCapitalization")
        @NlsContexts.ProgressText
        get() = KotlinJ2KBundle.message("text.converting.java.to.kotlin")
    private val fileCount = files?.size ?: 0
    private val fileCountText
        @Nls
        get() = KotlinJ2KBundle.message("text.files.count.0", fileCount, if (fileCount == 1) 1 else 2)
    private var fraction = 0.0
    private var pass = 1

    override fun <TInputItem, TOutputItem> processItems(
        fractionPortion: Double,
        inputItems: Iterable<TInputItem>,
        processItem: (TInputItem) -> TOutputItem
    ): List<TOutputItem> {
        val outputItems = ArrayList<TOutputItem>()
        // we use special process with EmptyProgressIndicator to avoid changing text in our progress by inheritors search inside etc
        ProgressManager.getInstance().runProcess(
            {
                progress?.text = "$progressText ($fileCountText) - ${KotlinJ2KBundle.message("text.pass.of.3", pass)}"
                progress?.isIndeterminate = false

                for ((i, item) in inputItems.withIndex()) {
                    progress?.checkCanceled()
                    progress?.fraction = fraction + fractionPortion * i / fileCount

                    progress?.text2 = files!![i].virtualFile.presentableUrl

                    outputItems.add(processItem(item))
                }

                pass++
                fraction += fractionPortion
            },
            EmptyProgressIndicator()
        )
        return outputItems
    }

    override fun <T> process(action: () -> T): T {
        throw AbstractMethodError("Should not be called for old J2K")
    }

    override fun updateState(fileIndex: Int?, phase: Int, description: String) {
        throw AbstractMethodError("Should not be called for old J2K")
    }

    override fun updateState(
        phase: Int,
        subPhase: Int,
        subPhaseCount: Int,
        fileIndex: Int?,
        description: String
    ) {
        error("Should not be called for old J2K")
    }
}