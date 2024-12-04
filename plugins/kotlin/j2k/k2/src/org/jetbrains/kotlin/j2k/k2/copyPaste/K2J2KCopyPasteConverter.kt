// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.k2.copyPaste

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.asTextRange
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind
import org.jetbrains.kotlin.j2k.copyPaste.*
import org.jetbrains.kotlin.name.FqName

private val LOG = Logger.getInstance(K2J2KCopyPasteConverter::class.java)

internal class K2J2KCopyPasteConverter(
    private val project: Project,
    private val editor: Editor,
    private val elementsAndTexts: ElementAndTextList,
    private val targetData: TargetData,
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
        ThreadingAssertions.assertEventDispatchThread()
        
        if (!::result.isInitialized && convertAndRestoreReferencesIfTextIsUnchanged()) return

        val (changedText, importsToAdd, converterContext) = result
        if (changedText == null) {
            LOG.error(
                "'changedText' is null, this is a logical error: " +
                        "the case with unchanged text should have been handled by `convertAndRestoreReferencesIfTextIsUnchanged`"
            )
            return
        }

        val endOffsetAfterReplace = targetData.bounds.startOffset + changedText.length
        val boundsAfterReplace = TextRange(targetData.bounds.startOffset, endOffsetAfterReplace)
        runWriteAction {
            targetData.document.replaceString(targetData.bounds.startOffset, targetData.bounds.endOffset, changedText)
            editor.caretModel.moveToOffset(endOffsetAfterReplace)
            PsiDocumentManager.getInstance(project).commitDocument(targetData.document)
        }

        val newBounds = insertImports(boundsAfterReplace, importsToAdd)
        runPostProcessing(project, targetData.file, newBounds, converterContext, Kind.K2)
    }

    override fun convertAndRestoreReferencesIfTextIsUnchanged(): Boolean {
        ThreadingAssertions.assertEventDispatchThread()

        val conversionResult = elementsAndTexts.convertCodeToKotlin(project, targetData.file, Kind.K2)
        val (text, _, importsToAdd, isTextChanged, converterContext) = conversionResult
        val changedText = if (isTextChanged) text else null
        result = Result(changedText, importsToAdd, converterContext)

        if (result.changedText != null) return false
        val boundsTextRange = targetData.bounds.asTextRange ?: return true
        insertImports(boundsTextRange, result.importsToAdd)
        return true
    }

    /**
     * Inserts necessary imports for converted code in [bounds].
     * @return The updated TextRange of converted code
     */
    private fun insertImports(bounds: TextRange, importsToAdd: Collection<FqName>): TextRange? {
        if (importsToAdd.isEmpty()) return bounds

        val rangeMarker = targetData.document.createRangeMarker(bounds).apply {
            isGreedyToLeft = true
            isGreedyToRight = true
        }
        runWriteAction {
            for (import in importsToAdd) {
                targetData.file.addImport(import)
            }
        }

        return rangeMarker.asTextRange
    }
}
