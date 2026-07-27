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

/**
 * Before conversion, runs all registered custom preprocessor extensions (i.e., classes implementing `J2kPreprocessorExtension` and
 * registered in their parent plugin's xml file).
 */
object PreprocessorExtensionsRunner :
    J2kExtensionsRunner<PsiJavaFile, J2kPreprocessorExtension>()

/**
 * After conversion, runs all registered custom postprocessor extensions (i.e., classes implementing `J2kPostprocessorExtension` and
 * registered in their parent plugin's xml file).
 */
object PostprocessorExtensionsRunner :
    J2kExtensionsRunner<KtFile, J2kPostprocessorExtension>()

abstract class J2kExtensionsRunner<T : PsiFile, U : J2kExtension<T>> {
    suspend fun runProcessors(project: Project, files: List<T>, processors: List<U>) {
        for (processor in processors) {
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
}
