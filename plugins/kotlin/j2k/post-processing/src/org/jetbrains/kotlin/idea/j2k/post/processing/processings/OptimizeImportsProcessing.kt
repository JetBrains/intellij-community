// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.j2k.post.processing.processings

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.RangeMarker
import com.intellij.refactoring.suggested.range
import org.jetbrains.kotlin.idea.imports.KotlinImportOptimizer
import org.jetbrains.kotlin.idea.j2k.post.processing.FileBasedPostProcessing
import org.jetbrains.kotlin.idea.j2k.post.processing.runUndoTransparentActionInEdt
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange

/**
 * NOTE: This class is J2K-specific, do not confuse it with [com.intellij.codeInsight.actions.OptimizeImportsProcessor].
 */
internal class OptimizeImportsProcessing : FileBasedPostProcessing() {
    override fun runProcessing(file: KtFile, allFiles: List<KtFile>, rangeMarker: RangeMarker?, converterContext: NewJ2kConverterContext) {
        val elements = runReadAction {
            when {
                rangeMarker != null && rangeMarker.isValid -> file.elementsInRange(rangeMarker.range!!)
                rangeMarker != null && !rangeMarker.isValid -> emptyList()
                else -> file.children.asList()
            }
        }
        val shouldOptimize = elements.any { element ->
            element is KtElement
                    && element !is KtImportDirective
                    && element !is KtImportList
                    && element !is KtPackageDirective
        }
        if (shouldOptimize) {
            val importsReplacer = runReadAction { KotlinImportOptimizer().processFile(file) }
            runUndoTransparentActionInEdt(inWriteAction = true) {
                importsReplacer.run()
            }
        }
    }
}