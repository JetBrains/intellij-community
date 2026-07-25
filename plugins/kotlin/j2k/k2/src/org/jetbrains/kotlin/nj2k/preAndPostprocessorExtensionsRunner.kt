// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import java.util.concurrent.CancellationException
import org.jetbrains.kotlin.j2k.J2kExtension
import org.jetbrains.kotlin.j2k.J2kPostprocessorExtension
import org.jetbrains.kotlin.j2k.J2kPreprocessorExtension
import org.jetbrains.kotlin.psi.KtFile

object J2kExtensionsRunner {
    suspend fun runPostProcessors(project: Project, files: List<KtFile>) {
        val processors = J2kPostprocessorExtension.EP_NAME.extensionList
        for (processor in processors) {
            executeProcessing(processor, project, files)
        }
    }

    suspend fun runPreProcessors(project: Project, files: List<PsiJavaFile>) {
        val processors = J2kPreprocessorExtension.EP_NAME.extensionList
        for (processor in processors) {
            executeProcessing(processor, project, files)
        }
    }

    private suspend fun <T : PsiFile> executeProcessing(
        processor: J2kExtension<T>,
        project: Project,
        files: List<T>
    ) {
        checkCanceled()
        try {
            processor.processFiles(project, files)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Logger.getInstance(J2kExtensionsRunner::class.java).error(t)
        }
    }
}
