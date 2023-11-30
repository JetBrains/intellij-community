// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.processings

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.RangeMarker
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.j2k.post.processing.FileBasedPostProcessing
import org.jetbrains.kotlin.nj2k.JKImportStorage
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtQualifiedExpression

internal class ShortenReferenceProcessing : FileBasedPostProcessing() {
    private val filter = filter@{ element: PsiElement ->
        when (element) {
            is KtQualifiedExpression -> when {
                JKImportStorage.isImportNeededForCall(element) -> ShortenReferences.FilterResult.PROCESS
                else -> ShortenReferences.FilterResult.SKIP
            }

            else -> ShortenReferences.FilterResult.PROCESS
        }
    }

    override fun runProcessing(file: KtFile, allFiles: List<KtFile>, rangeMarker: RangeMarker?, converterContext: NewJ2kConverterContext) {
        if (rangeMarker != null) {
            if (runReadAction { rangeMarker.isValid }) {
                ShortenReferences.DEFAULT.process(
                    file,
                    runReadAction { rangeMarker.startOffset },
                    runReadAction { rangeMarker.endOffset },
                    filter,
                    runImmediately = false
                )
            }
        } else {
            ShortenReferences.DEFAULT.process(file, filter, runImmediately = false)
        }
    }
}
