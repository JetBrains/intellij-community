// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.j2k.copyPaste.ConversionData
import org.jetbrains.kotlin.j2k.copyPaste.J2KCopyPasteConverter
import org.jetbrains.kotlin.j2k.copyPaste.PlainTextPasteImportResolver
import org.jetbrains.kotlin.j2k.copyPaste.TargetData
import org.jetbrains.kotlin.nj2k.Conversion
import org.jetbrains.kotlin.psi.KtFile

abstract class J2kConverterExtension {
    enum class Kind { K1_OLD, K1_NEW, K2 }

    abstract val kind: Kind

    abstract fun createJavaToKotlinConverter(
        project: Project,
        targetModule: Module?,
        settings: ConverterSettings,
        targetFile: KtFile? = null
    ): JavaToKotlinConverter

    abstract fun createPostProcessor(formatCode: Boolean = true): PostProcessor

    open fun doCheckBeforeConversion(project: Project, module: Module): Boolean =
        J2KKotlinConfigurationHelper.checkKotlinIsConfigured(module)

    open fun setUpAndConvert(
        project: Project,
        module: Module,
        javaFiles: List<PsiJavaFile>,
        convertFunction: (List<PsiJavaFile>, Project, Module) -> Unit
    ) = J2KKotlinConfigurationHelper.setUpAndConvert(project, module, javaFiles, convertFunction)

    abstract fun createWithProgressProcessor(
        progress: ProgressIndicator?,
        files: List<PsiJavaFile>?,
        phasesCount: Int
    ): WithProgressProcessor

    open fun getConversions(context: ConverterContext): List<Conversion> =
        emptyList()

    abstract fun createPlainTextPasteImportResolver(
        conversionData: ConversionData,
        targetKotlinFile: KtFile
    ): PlainTextPasteImportResolver

    abstract fun createCopyPasteConverter(
        project: Project,
        editor: Editor,
        conversionData: ConversionData,
        targetData: TargetData,
    ): J2KCopyPasteConverter

    companion object {
        val EP_NAME = ExtensionPointName<J2kConverterExtension>("org.jetbrains.kotlin.j2kConverterExtension")

        fun extension(kind: Kind): J2kConverterExtension = EP_NAME.extensionList.first { it.kind == kind }
    }
}