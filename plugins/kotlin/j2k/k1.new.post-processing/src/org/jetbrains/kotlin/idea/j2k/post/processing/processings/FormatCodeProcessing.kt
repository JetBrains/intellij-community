// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.j2k.post.processing.processings

import com.intellij.openapi.editor.RangeMarker
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.idea.j2k.post.processing.FileBasedPostProcessing
import org.jetbrains.kotlin.idea.j2k.post.processing.runUndoTransparentActionInEdt
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.psi.KtFile

internal class FormatCodeProcessing : FileBasedPostProcessing() {
    override fun runProcessing(file: KtFile, allFiles: List<KtFile>, rangeMarker: RangeMarker?, converterContext: NewJ2kConverterContext) {
        val codeStyleManager = CodeStyleManager.getInstance(file.project)
        runUndoTransparentActionInEdt(inWriteAction = true) {
            if (rangeMarker != null) {
                if (rangeMarker.isValid) {
                    codeStyleManager.reformatRange(file, rangeMarker.startOffset, rangeMarker.endOffset)
                }
            } else {
                codeStyleManager.reformat(file)
            }
        }
    }
}