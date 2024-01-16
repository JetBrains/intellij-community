// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJavaFile

class OldJ2kConverterExtension : J2kConverterExtension() {
    override val isNewJ2k = false

    override fun createJavaToKotlinConverter(
        project: Project,
        targetModule: Module?,
        settings: ConverterSettings
    ): JavaToKotlinConverter =
        OldJavaToKotlinConverter(project, settings)

    override fun createPostProcessor(formatCode: Boolean): PostProcessor =
        J2kPostProcessor(formatCode)

    override fun createWithProgressProcessor(
        progress: ProgressIndicator?,
        files: List<PsiJavaFile>?,
        phasesCount: Int
    ): WithProgressProcessor =
        OldWithProgressProcessor(progress, files)
}