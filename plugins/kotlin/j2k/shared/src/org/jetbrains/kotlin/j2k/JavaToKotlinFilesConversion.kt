// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
suspend fun convertJavaFilesToKotlin(
    files: List<PsiJavaFile>,
    project: Project,
    module: Module,
    bodyFilter: ((PsiElement) -> Boolean)? = null,
    settings: ConverterSettings = ConverterSettings.defaultSettings,
    preprocessorExtensions: List<J2kPreprocessorExtension> = J2kPreprocessorExtension.EP_NAME.extensionList,
    postprocessorExtensions: List<J2kPostprocessorExtension> = J2kPostprocessorExtension.EP_NAME.extensionList,
): ConversionResult {
    val j2kConverterExtension = J2kConverterExtension.extension()
    val converter = j2kConverterExtension.createJavaToKotlinConverter(project, module, settings)
    val postProcessor = j2kConverterExtension.createPostProcessor()

    return converter.filesToKotlin(
        files,
        postProcessor,
        bodyFilter = bodyFilter,
        preprocessorExtensions = preprocessorExtensions,
        postprocessorExtensions = postprocessorExtensions,
    )
}

