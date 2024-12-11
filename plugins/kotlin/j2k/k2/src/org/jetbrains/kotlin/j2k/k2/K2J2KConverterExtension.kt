// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.k2

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJavaFile
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.kotlin.j2k.*
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K2
import org.jetbrains.kotlin.j2k.copyPaste.TargetData
import org.jetbrains.kotlin.j2k.copyPaste.ConversionData
import org.jetbrains.kotlin.j2k.copyPaste.J2KCopyPasteConverter
import org.jetbrains.kotlin.j2k.copyPaste.PlainTextPasteImportResolver
import org.jetbrains.kotlin.j2k.k2.copyPaste.K2J2KCopyPasteConverter
import org.jetbrains.kotlin.j2k.k2.copyPaste.K2PlainTextPasteImportResolver
import org.jetbrains.kotlin.nj2k.Conversion
import org.jetbrains.kotlin.nj2k.NewJ2kWithProgressProcessor
import org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverter
import org.jetbrains.kotlin.psi.KtFile

// TODO: reuse NewJ2kConverterExtension.checkEverythingIsSetUpBeforeConversion() and NewJ2kConverterExtension.setUpAndConvert()
class K2J2KConverterExtension : J2kConverterExtension() {
    override val kind: Kind = K2

    override fun createJavaToKotlinConverter(
        project: Project,
        targetModule: Module?,
        settings: ConverterSettings,
        targetFile: KtFile?
    ): JavaToKotlinConverter =
        // TODO: rename/refactor
        NewJavaToKotlinConverter(project, targetModule, settings, targetFile)

    override fun createPostProcessor(formatCode: Boolean): PostProcessor =
        K2J2KPostProcessor()

    override fun createWithProgressProcessor(
        progress: ProgressIndicator?,
        files: List<PsiJavaFile>?,
        phasesCount: Int
    ): WithProgressProcessor =
        // TODO: rename/refactor
        NewJ2kWithProgressProcessor(progress, files, phasesCount)

    override fun getConversions(context: ConverterContext): List<Conversion> =
        getK2J2KConversions(context)

    override fun createPlainTextPasteImportResolver(
        conversionData: ConversionData,
        targetKotlinFile: KtFile
    ): PlainTextPasteImportResolver {
        ThreadingAssertions.assertBackgroundThread()
        return K2PlainTextPasteImportResolver(conversionData, targetKotlinFile)
    }

    override fun createCopyPasteConverter(
        project: Project,
        editor: Editor,
        conversionData: ConversionData,
        targetData: TargetData
    ): J2KCopyPasteConverter {
        return K2J2KCopyPasteConverter(project, editor, conversionData.elementsAndTexts, targetData)
    }
}