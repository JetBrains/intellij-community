// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.postProcessings

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.RangeMarker
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.ShortenCommand
import org.jetbrains.kotlin.idea.base.analysis.api.utils.invokeShortening
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.j2k.FileBasedPostProcessing
import org.jetbrains.kotlin.j2k.PostProcessingApplier
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.runUndoTransparentActionInEdt
import org.jetbrains.kotlin.psi.KtFile

// TODO is it necessary to use `JKImportStorage.isImportNeededForCall`, like in K1?
internal class K2ShortenReferenceProcessing : FileBasedPostProcessing() {
    override fun runProcessing(file: KtFile, allFiles: List<KtFile>, rangeMarker: RangeMarker?, converterContext: NewJ2kConverterContext) {
        val range = runReadAction {
            if (rangeMarker != null && rangeMarker.isValid) rangeMarker.textRange else file.textRange
        }

        runUndoTransparentActionInEdt(inWriteAction = true) {
            ShortenReferencesFacility.getInstance().shorten(file, range)
        }
    }

    context(KtAnalysisSession)
    override fun computeApplier(
        file: KtFile,
        allFiles: List<KtFile>,
        rangeMarker: RangeMarker?,
        converterContext: NewJ2kConverterContext
    ): PostProcessingApplier {
        val range = if (rangeMarker != null && rangeMarker.isValid) rangeMarker.textRange else file.textRange
        val shortenCommand = collectPossibleReferenceShortenings(file, range)
        return Applier(shortenCommand)
    }

    private class Applier(private val shortenCommand: ShortenCommand): PostProcessingApplier {
        override fun apply() {
            shortenCommand.invokeShortening()
        }
    }
}