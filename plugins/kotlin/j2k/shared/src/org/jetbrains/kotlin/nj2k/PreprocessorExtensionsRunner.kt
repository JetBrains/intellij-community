// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJavaFile
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.codeinsight.utils.commitAndUnblockDocument
import org.jetbrains.kotlin.j2k.J2kPreprocessorExtension

/**
 * Before conversion, runs all registered custom preprocessor extensions (i.e. classes implementing `J2kPreprocessorExtension` and
 * registered in their parent plugin's xml file).
 */
object PreprocessorExtensionsRunner {

    private const val PHASE_NAME = "Custom Preprocessing"
    val logger = Logger.getInstance(this::class.java)

    fun runRegisteredPreprocessors(project: Project, javaFiles: List<PsiJavaFile>, preprocessorExtensions: List<J2kPreprocessorExtension> = J2kPreprocessorExtension.EP_NAME.extensionList) {
        logger.warn("runRegisteredPreprocessors start, thread = ${Thread.currentThread().name}")
        if (preprocessorExtensions.isEmpty()) return

        val preprocessorsCount = preprocessorExtensions.size
        ProgressManager.progress(PHASE_NAME, "Found $preprocessorsCount preprocessors to run on Java files before conversion")

        for ((i, preprocessor) in preprocessorExtensions.withIndex()) {
            ProgressManager.checkCanceled()
            ProgressManager.progress(PHASE_NAME, "Running preprocessor $i/$preprocessorsCount")
            try {
                logger.warn("just before runBlockingCancellable, thread = ${Thread.currentThread().name}")
                runBlockingCancellable {
                    logger.warn("just inside runBlockingCancellable, thread = ${Thread.currentThread().name} ${this.coroutineContext}")
                    preprocessor.processFiles(project, javaFiles)
                }
                logger.warn("just after runBlockingCancellable but before commitAllDocuments, thread = ${Thread.currentThread().name}")
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
