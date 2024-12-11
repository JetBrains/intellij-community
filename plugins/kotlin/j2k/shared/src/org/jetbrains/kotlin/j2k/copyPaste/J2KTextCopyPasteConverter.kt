// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.copyPaste

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.asTextRange
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.j2k.J2kConverterExtension
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K1_OLD
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
    private val j2kKind: J2kConverterExtension.Kind,
) {
    fun convert() {
        val additionalImports = tryToResolveImports(conversionData, targetData.file)
        ProgressManager.checkCanceled()

        val importsInsertOffset = targetData.file.importList?.endOffset ?: 0
        var convertedImportsText = additionalImports.convertCodeToKotlin(project, targetData.file, j2kKind).text
        if (targetData.file.importDirectives.isEmpty() && importsInsertOffset > 0) {
            convertedImportsText = "\n" + convertedImportsText
        }

        val conversionResult = conversionData.elementsAndTexts.convertCodeToKotlin(project, targetData.file, j2kKind)
        val convertedText = conversionResult.text
        ProgressManager.checkCanceled()

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

        if (j2kKind != K1_OLD) {
            val postProcessor = J2kConverterExtension.extension(j2kKind).createPostProcessor()
            for (fqName in conversionResult.importsToAdd) {
                postProcessor.insertImport(targetData.file, fqName)
            }
        }

        ProgressManager.checkCanceled()
        runPostProcessing(project, targetData.file, boundsAfterReplace.asTextRange, conversionResult.converterContext, j2kKind)
    }

    private fun tryToResolveImports(conversionData: ConversionData, targetFile: KtFile): ElementAndTextList {
        return ProgressManager.getInstance().runProcessWithProgressSynchronously(
            ThrowableComputable {
                val resolver = J2kConverterExtension.extension(j2kKind).createPlainTextPasteImportResolver(conversionData, targetFile)
                val imports = resolver.generateRequiredImports()
                val newlineSeparatedImports = imports.flatMap { importStatement ->
                    listOf("\n", importStatement)
                } + "\n\n"

                ElementAndTextList(newlineSeparatedImports)
            },
            KotlinNJ2KBundle.message("copy.text.adding.imports"),
            /* canBeCanceled = */ true,
            project
        )
    }
}
