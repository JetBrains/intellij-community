// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.j2k.copyPaste.K1J2KCopyPasteConverter
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K1_OLD
import org.jetbrains.kotlin.j2k.copyPaste.DataForConversion
import org.jetbrains.kotlin.j2k.copyPaste.J2KCopyPasteConverter
import org.jetbrains.kotlin.j2k.copyPaste.K1PlainTextPasteImportResolver
import org.jetbrains.kotlin.j2k.copyPaste.PlainTextPasteImportResolver
import org.jetbrains.kotlin.psi.KtFile

class OldJ2kConverterExtension : J2kConverterExtension() {
    override val kind: Kind = K1_OLD

    override fun createJavaToKotlinConverter(
        project: Project,
        targetModule: Module?,
        settings: ConverterSettings,
        targetFile: KtFile?
    ): JavaToKotlinConverter =
        OldJavaToKotlinConverter(project, settings)

    override fun createPostProcessor(formatCode: Boolean): PostProcessor =
        OldJ2kPostProcessor(formatCode)

    override fun createWithProgressProcessor(
        progress: ProgressIndicator?,
        files: List<PsiJavaFile>?,
        phasesCount: Int
    ): WithProgressProcessor =
        OldWithProgressProcessor(progress, files)

    override fun doCheckBeforeConversion(project: Project, module: Module): Boolean =
        true

    override fun createPlainTextPasteImportResolver(
        dataForConversion: DataForConversion,
        targetKotlinFile: KtFile
    ): PlainTextPasteImportResolver {
        return K1PlainTextPasteImportResolver(dataForConversion, targetKotlinFile)
    }

    override fun createCopyPasteConverter(
        project: Project,
        editor: Editor,
        dataForConversion: DataForConversion,
        j2kKind: Kind,
        targetFile: KtFile,
        targetBounds: RangeMarker,
        targetDocument: Document
    ): J2KCopyPasteConverter {
        return K1J2KCopyPasteConverter(project, editor, dataForConversion, j2kKind, targetFile, targetBounds, targetDocument)
    }
}