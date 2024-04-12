// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.postProcessings

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.asTextRange
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinOptimizeImportsFacility
import org.jetbrains.kotlin.j2k.FileBasedPostProcessing
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.runUndoTransparentActionInEdt
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange

/**
 * NOTE: This class is J2K-specific, do not confuse it with [com.intellij.codeInsight.actions.OptimizeImportsProcessor].
 */
class OptimizeImportsProcessing : FileBasedPostProcessing() {
    override fun runProcessing(file: KtFile, allFiles: List<KtFile>, rangeMarker: RangeMarker?, converterContext: NewJ2kConverterContext) {
        if (!shouldTryToOptimizeImports(file, rangeMarker)) return

        val optimizeImportsFacility = KotlinOptimizeImportsFacility.getInstance()
        val optimizedImports = runReadAction {
            val importData = optimizeImportsFacility.analyzeImports(file) ?: return@runReadAction null
            optimizeImportsFacility.prepareOptimizedImports(file, importData)
        }

        if (optimizedImports != null) {
            runUndoTransparentActionInEdt(inWriteAction = true) {
                optimizeImportsFacility.replaceImports(file, optimizedImports)
            }
        }
    }

    private fun shouldTryToOptimizeImports(file: KtFile, rangeMarker: RangeMarker?): Boolean {
        val elements = runReadAction {
            when {
                rangeMarker != null && rangeMarker.isValid -> file.elementsInRange(rangeMarker.asTextRange!!)
                rangeMarker != null && !rangeMarker.isValid -> emptyList()
                else -> file.children.asList()
            }
        }
        return elements.any {
            it is KtElement
                    && it !is KtImportDirective
                    && it !is KtImportList
                    && it !is KtPackageDirective
        }
    }
}