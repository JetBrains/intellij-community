// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.copyPaste

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.asTextRange
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.j2k.J2kConverterExtension
import org.jetbrains.kotlin.nj2k.KotlinNJ2KBundle
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.endOffset

/**
 * Runs J2K on the pasted code and updates the target file as a side effect.
 * Used by [ConvertTextJavaCopyPasteProcessor].
 */
internal class J2KTextCopyPasteConverter(
    private val project: Project,
    private val editor: Editor,
    private val conversionData: ConversionData,
    private val targetData: TargetData,
) {
    fun convert() {
        val additionalImports = tryToResolveImports(conversionData, targetData.file)

        val importsInsertOffset = targetData.file.importList?.endOffset ?: 0
        var convertedImportsText = additionalImports.convertCodeToKotlin(project, targetData.file).text
        if (targetData.file.importDirectives.isEmpty() && importsInsertOffset > 0) {
            convertedImportsText = "\n" + convertedImportsText
        }

        val conversionResult = conversionData.elementsAndTexts.convertCodeToKotlin(project, targetData.file)
        val convertedText = conversionResult.text

        val boundsAfterReplace = runWriteAction {
            if (convertedImportsText.isNotBlank()) {
                targetData.document.insertString(importsInsertOffset, convertedImportsText)
            }
            targetData.document.replaceString(targetData.bounds.startOffset, targetData.bounds.endOffset, convertedText)

            val endOffsetAfterReplace = targetData.bounds.startOffset + convertedText.length
            editor.caretModel.moveToOffset(endOffsetAfterReplace)

            targetData.document.createRangeMarker(targetData.bounds.startOffset, endOffsetAfterReplace)
        }

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val postProcessor = J2kConverterExtension.extension().createPostProcessor()
        for (fqName in conversionResult.importsToAdd) {
            postProcessor.insertImport(targetData.file, fqName)
        }

        runPostProcessing(project, targetData.file, boundsAfterReplace.asTextRange, conversionResult.converterContext)
    }

    private fun tryToResolveImports(conversionData: ConversionData, targetFile: KtFile): ElementAndTextList {
        return runWithModalProgressBlocking(project, KotlinNJ2KBundle.message("copy.text.adding.imports")) {
            val resolver = readAction {
                J2kConverterExtension.extension().createPlainTextPasteImportResolver(conversionData, targetFile)
            }
            val imports = resolver.generateRequiredImports()
            val newlineSeparatedImports = imports.flatMap { importStatement ->
                listOf("\n", importStatement)
            } + "\n\n"

            ElementAndTextList(newlineSeparatedImports)
        }
    }
}
