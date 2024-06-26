// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.codeinsight.utils.commitAndUnblockDocument
import org.jetbrains.kotlin.j2k.J2kPostprocessorExtension
import org.jetbrains.kotlin.psi.KtFile

/**
 * Before conversion, runs all registered custom postprocessor extensions (i.e. classes implementing `J2kPostprocessorExtension` and
 * registered in their parent plugin's xml file).
 */
object PostprocessorExtensionsRunner {
    private const val PHASE_NAME = "Custom Postprocessing"

    fun runRegisteredPostprocessors(project: Project, ktFiles: List<KtFile>, postprocessorExtensions: List<J2kPostprocessorExtension>) {
        if (postprocessorExtensions.isEmpty()) return
        val postprocessorsCount = postprocessorExtensions.size
        ProgressManager.progress(PHASE_NAME, "Found $postprocessorsCount postprocessors to run on Java files before conversion")
        for ((i, postprocessor) in postprocessorExtensions.withIndex()) {
            ProgressManager.checkCanceled()
            ProgressManager.progress(PHASE_NAME, "Running postprocessor ${i + 1}/$postprocessorsCount")
            try {
                runBlockingCancellable {
                    postprocessor.processFiles(project, ktFiles)
                }
                commitAllDocuments(ktFiles)
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (t: Throwable) {
                commitAllDocuments(ktFiles)
                throw t
            }
        }
    }

    private fun commitAllDocuments(ktFiles: List<KtFile>) {
        ktFiles.forEach {
            runUndoTransparentActionInEdt(inWriteAction = true) {
                it.commitAndUnblockDocument()
            }
        }
    }
}
