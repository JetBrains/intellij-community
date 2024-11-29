// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.copyPaste

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.asTextRange
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.j2k.J2kConverterExtension
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K1_OLD
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.endOffset

/**
 * Runs J2K on the pasted code and updates [targetFile] as a side effect.
 * Used by [ConvertTextJavaCopyPasteProcessor].
 */
internal class J2KTextCopyPasteConverter(
    private val project: Project,
    private val editor: Editor,
    private val dataForConversion: DataForConversion,
    private val j2kKind: J2kConverterExtension.Kind,
    private val targetFile: KtFile,
    private val targetBounds: RangeMarker,
    private val targetDocument: Document
) {
    fun convert() {
        val additionalImports = tryToResolveImports(dataForConversion, targetFile)
        ProgressManager.checkCanceled()

        val importsInsertOffset = targetFile.importList?.endOffset ?: 0
        var convertedImportsText = additionalImports.convertCodeToKotlin(project, targetFile, j2kKind).text
        if (targetFile.importDirectives.isEmpty() && importsInsertOffset > 0) {
            convertedImportsText = "\n" + convertedImportsText
        }

        val conversionResult = dataForConversion.elementsAndTexts.convertCodeToKotlin(project, targetFile, j2kKind)
        val convertedText = conversionResult.text
        ProgressManager.checkCanceled()

        val boundsAfterReplace = runWriteAction {
            if (convertedImportsText.isNotBlank()) {
                targetDocument.insertString(importsInsertOffset, convertedImportsText)
            }
            targetDocument.replaceString(targetBounds.startOffset, targetBounds.endOffset, convertedText)

            val endOffsetAfterReplace = targetBounds.startOffset + convertedText.length
            editor.caretModel.moveToOffset(endOffsetAfterReplace)

            targetDocument.createRangeMarker(targetBounds.startOffset, endOffsetAfterReplace)
        }

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        if (j2kKind != K1_OLD) {
            val postProcessor = J2kConverterExtension.extension(j2kKind).createPostProcessor()
            for (fqName in conversionResult.importsToAdd) {
                postProcessor.insertImport(targetFile, fqName)
            }
        }

        ProgressManager.checkCanceled()
        runPostProcessing(project, targetFile, boundsAfterReplace.asTextRange, conversionResult.converterContext, j2kKind)
    }

    private fun tryToResolveImports(dataForConversion: DataForConversion, targetFile: KtFile): ElementAndTextList {
        val resolver = J2kConverterExtension.extension(j2kKind).createPlainTextPasteImportResolver(dataForConversion, targetFile)
        val imports = resolver.generateRequiredImports()
        val newlineSeparatedImports = imports.flatMap { importStatement ->
            listOf("\n", importStatement)
        } + "\n\n"

        return ElementAndTextList(newlineSeparatedImports)
    }
}
