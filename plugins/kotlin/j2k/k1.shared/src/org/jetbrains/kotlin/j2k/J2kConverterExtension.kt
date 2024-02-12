// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJavaFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class J2kConverterExtension {
    enum class Kind { K1_OLD, K1_NEW, K2 }

    abstract val kind: Kind

    abstract fun createJavaToKotlinConverter(
        project: Project,
        targetModule: Module?,
        settings: ConverterSettings
    ): JavaToKotlinConverter

    abstract fun createPostProcessor(formatCode: Boolean = true): PostProcessor

    open fun doCheckBeforeConversion(project: Project, module: Module): Boolean =
        true

    abstract fun createWithProgressProcessor(
        progress: ProgressIndicator?,
        files: List<PsiJavaFile>?,
        phasesCount: Int
    ): WithProgressProcessor

    companion object {
        val EP_NAME = ExtensionPointName<J2kConverterExtension>("org.jetbrains.kotlin.j2kConverterExtension")

        fun extension(kind: Kind): J2kConverterExtension = EP_NAME.extensionList.first { it.kind == kind }
    }
}