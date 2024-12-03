// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.k2.copyPaste

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.asTextRange
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.J2kConverterExtension
import org.jetbrains.kotlin.j2k.copyPaste.DataForConversion
import org.jetbrains.kotlin.j2k.copyPaste.J2KCopyPasteConverter
import org.jetbrains.kotlin.j2k.copyPaste.convertCodeToKotlin
import org.jetbrains.kotlin.j2k.copyPaste.runPostProcessing
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

internal class K2J2KCopyPasteConverter(
    private val project: Project,
    private val editor: Editor,
    private val dataForConversion: DataForConversion,
    private val j2kKind: J2kConverterExtension.Kind,
    private val targetFile: KtFile,
    private val targetBounds: RangeMarker,
    private val targetDocument: Document
) : J2KCopyPasteConverter {
    /**
     * @property changedText The transformed Kotlin code, or `null` if no conversion occurred (the result is the same as original code).
     * @property importsToAdd A set of fully qualified names to be explicitly imported in the converted Kotlin file.
     */
    private data class Result(
        val changedText: String?,
        val importsToAdd: Set<FqName>,
        val converterContext: ConverterContext?
    )

    private lateinit var result: Result

    override fun convert() {
        if (!::result.isInitialized && convertAndRestoreReferencesIfTextIsUnchanged()) return

        val (changedText, importsToAdd, converterContext) = result
        checkNotNull(changedText) // the case with unchanged text has already been handled by `convertAndRestoreReferencesIfTextIsUnchanged`

        val endOffsetAfterReplace = targetBounds.startOffset + changedText.length
        val boundsAfterReplace = TextRange(targetBounds.startOffset, endOffsetAfterReplace)
        runWriteAction {
            targetDocument.replaceString(targetBounds.startOffset, targetBounds.endOffset, changedText)
            editor.caretModel.moveToOffset(endOffsetAfterReplace)
        }

        val newBounds = insertImports(boundsAfterReplace, importsToAdd)
        PsiDocumentManager.getInstance(project).commitDocument(targetDocument)
        runPostProcessing(project, targetFile, newBounds, converterContext, j2kKind)
    }

    override fun convertAndRestoreReferencesIfTextIsUnchanged(): Boolean {
        val conversionResult = dataForConversion.elementsAndTexts.convertCodeToKotlin(project, targetFile, j2kKind)
        val (text, _, importsToAdd, isTextChanged, converterContext) = conversionResult
        val changedText = if (isTextChanged) text else null
        result = Result(changedText, importsToAdd, converterContext)

        if (result.changedText != null) return false
        val boundsTextRange = targetBounds.asTextRange ?: return true
        insertImports(boundsTextRange, result.importsToAdd)
        return true
    }

    /**
     * Inserts necessary imports for converted code in [bounds].
     * @return The updated TextRange of converted code
     */
    private fun insertImports(bounds: TextRange, importsToAdd: Collection<FqName>): TextRange? {
        if (importsToAdd.isEmpty()) return bounds

        PsiDocumentManager.getInstance(project).commitDocument(targetDocument)
        val rangeMarker = targetDocument.createRangeMarker(bounds).apply {
            isGreedyToLeft = true
            isGreedyToRight = true
        }
        runWriteAction {
            for (import in importsToAdd) {
                targetFile.addImport(import)
            }
        }

        return rangeMarker.asTextRange
    }
}
