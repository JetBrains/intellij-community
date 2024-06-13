// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.idea.codeinsight.utils.commitAndUnblockDocument
import org.jetbrains.kotlin.j2k.J2kPreprocessorExtension

/**
 * Before conversion, runs all registered custom preprocessor extensions (i.e. classes implementing `J2kPreprocessorExtension` and
 * registered in their parent plugin's xml file).
 */
object PreprocessorExtensionsRunner {

    private const val PHASE_NAME = "Custom Preprocessing"

    fun runRegisteredPreprocessors(project: Project, javaFiles: List<PsiJavaFile>) {
        val preprocessorExtensions = J2kPreprocessorExtension.EP_NAME.extensionList
        if (preprocessorExtensions.isEmpty()) return

        val preprocessorsCount = preprocessorExtensions.size
        ProgressManager.progress(PHASE_NAME, "Found $preprocessorsCount preprocessors to run on Java files before conversion")

        for ((i, preprocessor) in preprocessorExtensions.withIndex()) {
            ProgressManager.checkCanceled()
            ProgressManager.progress(PHASE_NAME, "Running preprocessor $i/$preprocessorsCount")
            try {
                preprocessor.processFiles(project, javaFiles)
                commitAllDocuments(javaFiles)
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (t: Throwable) {
                commitAllDocuments(javaFiles)
            }
        }
    }

    private fun commitAllDocuments(javaFiles: List<PsiJavaFile>) {
        javaFiles.forEach {
            runUndoTransparentActionInEdt(inWriteAction = true) {
                it.commitAndUnblockDocument()
            }
        }
    }
}
