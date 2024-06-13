// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile

abstract class JavaToKotlinConverter {
    protected abstract fun elementsToKotlin(inputElements: List<PsiElement>, processor: WithProgressProcessor): Result

    abstract fun filesToKotlin(
        files: List<PsiJavaFile>,
        postProcessor: PostProcessor,
        progressIndicator: ProgressIndicator = EmptyProgressIndicator(),
        preprocessorExtensions: List<J2kPreprocessorExtension> = emptyList(),
        postprocessorExtensions: List<J2kPostprocessorExtension> = emptyList()
    ): FilesResult

    abstract fun elementsToKotlin(inputElements: List<PsiElement>): Result
}
