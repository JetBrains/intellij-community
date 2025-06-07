// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.postProcessings

import com.intellij.openapi.editor.RangeMarker
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.FileBasedPostProcessing
import org.jetbrains.kotlin.j2k.PostProcessingApplier
import org.jetbrains.kotlin.nj2k.runUndoTransparentActionInEdt
import org.jetbrains.kotlin.psi.KtFile

class FormatCodeProcessing : FileBasedPostProcessing() {
    override fun runProcessing(file: KtFile, allFiles: List<KtFile>, rangeMarker: RangeMarker?, converterContext: ConverterContext) {
        val codeStyleManager = CodeStyleManager.getInstance(file.project)
        runUndoTransparentActionInEdt(inWriteAction = true) {
            // TODO investigate why one formatting pass is not enough in some cases (KTIJ-29962)
            repeat(2) {
                when {
                    rangeMarker == null -> codeStyleManager.reformat(file)
                    rangeMarker.isValid -> codeStyleManager.reformatRange(file, rangeMarker.startOffset, rangeMarker.endOffset)
                }
            }
        }
    }

    override fun computeApplier(
        file: KtFile,
        allFiles: List<KtFile>,
        rangeMarker: RangeMarker?,
        converterContext: ConverterContext
    ): PostProcessingApplier = Applier(file.createSmartPointer(), rangeMarker)

    private class Applier(
        private val filePointer: SmartPsiElementPointer<KtFile>,
        private val rangeMarker: RangeMarker?
    ) : PostProcessingApplier {
        override fun apply() {
            // TODO investigate why one formatting pass is not enough in some cases (KTIJ-29962)
            repeat(2) {
                val file = filePointer.element ?: return
                val codeStyleManager = CodeStyleManager.getInstance(file.project)

                when {
                    rangeMarker == null -> codeStyleManager.reformat(file)
                    rangeMarker.isValid -> codeStyleManager.reformatRange(file, rangeMarker.startOffset, rangeMarker.endOffset)
                }
            }
        }
    }
}
