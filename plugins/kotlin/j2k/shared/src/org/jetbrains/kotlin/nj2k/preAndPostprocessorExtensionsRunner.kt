// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.idea.codeinsight.utils.commitAndUnblockDocument
import org.jetbrains.kotlin.j2k.J2kExtension
import org.jetbrains.kotlin.j2k.J2kPostprocessorExtension
import org.jetbrains.kotlin.j2k.J2kPreprocessorExtension
import org.jetbrains.kotlin.psi.KtFile

/**
 * Before conversion, runs all registered custom preprocessor extensions (i.e., classes implementing `J2kPreprocessorExtension` and
 * registered in their parent plugin's xml file).
 */
object PreprocessorExtensionsRunner :
    J2kExtensionsRunner<PsiJavaFile, J2kPreprocessorExtension>(KotlinNJ2KBundle.message("j2k.custom.preprocessing"))

/**
 * After conversion, runs all registered custom postprocessor extensions (i.e., classes implementing `J2kPostprocessorExtension` and
 * registered in their parent plugin's xml file).
 */
object PostprocessorExtensionsRunner :
    J2kExtensionsRunner<KtFile, J2kPostprocessorExtension>(KotlinNJ2KBundle.message("j2k.custom.postprocessing"))

abstract class J2kExtensionsRunner<T : PsiFile, U : J2kExtension<T>>(@NlsContexts.ProgressText private val phaseName: String) {
    fun runProcessors(project: Project, files: List<T>, processors: List<U>) {
        if (processors.isEmpty()) return
        val processorsCount = processors.size

        for ((i, processor) in processors.withIndex()) {
            ProgressManager.checkCanceled()
            val processorName = processor::class.java.simpleName
            ProgressManager.progress(
                phaseName,
                KotlinNJ2KBundle.message("j2k.custom.extensions.progress", processorName, i + 1, processorsCount)
            )
            try {
                runBlockingCancellable {
                    processor.processFiles(project, files)
                }
                commitAllDocuments(files)
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (t: Throwable) {
                Logger.getInstance(J2kExtensionsRunner::class.java).error(t)
                commitAllDocuments(files)
            }
        }
    }

    private fun commitAllDocuments(files: List<T>) {
        files.forEach {
            runUndoTransparentActionInEdt(inWriteAction = true) {
                it.commitAndUnblockDocument()
            }
        }
    }
}
