// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.idea.codeinsight.utils.commitAndUnblockDocument
import org.jetbrains.kotlin.j2k.J2kExtension
import org.jetbrains.kotlin.j2k.J2kPreprocessorExtension

/**
 * Before conversion, runs all registered custom preprocessor extensions (i.e. classes implementing `J2kPreprocessorExtension` and
 * registered in their parent plugin's xml file).
 */
object PreprocessorExtensionsRunner : J2kExtensionsRunner<PsiJavaFile, J2kPreprocessorExtension>("Custom Preprocessing")

abstract class J2kExtensionsRunner<T : PsiFile, U : J2kExtension<T>>(private val phaseName: String) {
    fun runProcessors(project: Project, files: List<T>, processorExtensions: List<U>) {
        if (processorExtensions.isEmpty()) return
        val preprocessorsCount = processorExtensions.size

        for ((i, preprocessor) in processorExtensions.withIndex()) {
            ProgressManager.checkCanceled()
            ProgressManager.progress(phaseName, "Running processor ${preprocessor::class.simpleName} (${i + 1}/$preprocessorsCount)")
            try {
                runBlockingCancellable {
                    preprocessor.processFiles(project, files)
                }
                commitAllDocuments(files)
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (t: Throwable) {
                commitAllDocuments(files)
            }
        }
    }

    private fun commitAllDocuments(javaFiles: List<T>) {
        javaFiles.forEach {
            runUndoTransparentActionInEdt(inWriteAction = true) {
                it.commitAndUnblockDocument()
            }
        }
    }
}
