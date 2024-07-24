// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing

import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.j2k.*
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K1_NEW
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.psi.KtFile

class NewJ2kConverterExtension : J2kConverterExtension() {
    override val kind: Kind = K1_NEW

    override fun createJavaToKotlinConverter(
        project: Project,
        targetModule: Module?,
        settings: ConverterSettings,
        targetFile: KtFile?
    ): JavaToKotlinConverter =
        NewJavaToKotlinConverter(project, targetModule, settings, targetFile)

    override fun createPostProcessor(formatCode: Boolean): PostProcessor =
        NewJ2kPostProcessor()

    override fun createWithProgressProcessor(
        progress: ProgressIndicator?,
        files: List<PsiJavaFile>?,
        phasesCount: Int
    ): WithProgressProcessor =
        NewJ2kWithProgressProcessor(progress, files, phasesCount)

    override fun getConversions(context: NewJ2kConverterContext): List<Conversion> =
        getNewJ2KConversions(context)
}