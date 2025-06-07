// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.copyPaste

import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.editor.KotlinEditorOptions
import org.jetbrains.kotlin.idea.statistics.ConversionType
import org.jetbrains.kotlin.idea.statistics.J2KFusCollector
import org.jetbrains.kotlin.j2k.J2kConverterExtension
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K1_NEW
import java.awt.datatransfer.Transferable
import kotlin.system.measureTimeMillis

private val LOG = Logger.getInstance(ConvertJavaCopyPasteProcessor::class.java)
private const val MAX_TEXT_LENGTH_TO_CONVERT_WITHOUT_ASKING_USER = 1000

/**
 * Handles the J2K conversion of copy-pasted code from a Java file into a Kotlin file.
 *
 * See also [ConvertTextJavaCopyPasteProcessor] for the related case of arbitrary code copy-pasted into a Kotlin file.
 *
 * Tests: [org.jetbrains.kotlin.j2k.k2.K2JavaToKotlinCopyPasteConversionTestGenerated].
 */
class ConvertJavaCopyPasteProcessor : CopyPastePostProcessor<TextBlockTransferableData>() {
    override fun extractTransferableData(content: Transferable): List<TextBlockTransferableData> {
        try {
            if (content.isDataFlavorSupported(CopiedJavaCode.DATA_FLAVOR)) {
                return listOf(content.getTransferData(CopiedJavaCode.DATA_FLAVOR) as TextBlockTransferableData)
            }
        } catch (e: Throwable) {
            if (e is ControlFlowException) throw e
            LOG.error(e)
        }
        return listOf()
    }

    override fun collectTransferableData(
        file: PsiFile,
        editor: Editor,
        startOffsets: IntArray,
        endOffsets: IntArray
    ): List<TextBlockTransferableData> {
        if (file !is PsiJavaFile) return listOf()
        return listOf(CopiedJavaCode(file.getText()!!, startOffsets, endOffsets))
    }

    override fun processTransferableData(
        project: Project,
        editor: Editor,
        bounds: RangeMarker,
        caretOffset: Int,
        indented: Ref<in Boolean>,
        values: List<TextBlockTransferableData>
    ) {
        if (DumbService.getInstance(project).isDumb) return
        if (!KotlinEditorOptions.getInstance().isEnableJavaToKotlinConversion) return

        val targetData = getTargetData(project, editor.document, caretOffset, bounds) ?: return
        if (!isConversionSupportedAtPosition(targetData.file, targetData.bounds.startOffset)) return

        val copiedJavaCode = values.single() as CopiedJavaCode
        val conversionData = ConversionData.prepare(copiedJavaCode, project)
        val j2kKind = getJ2kKind(targetData.file)

        val converter = J2kConverterExtension.extension(j2kKind)
            .createCopyPasteConverter(project, editor, conversionData, targetData)

        val textLength = copiedJavaCode.startOffsets.indices.sumOf { copiedJavaCode.endOffsets[it] - copiedJavaCode.startOffsets[it] }
        if (textLength < MAX_TEXT_LENGTH_TO_CONVERT_WITHOUT_ASKING_USER && converter.convertAndRestoreReferencesIfTextIsUnchanged()) {
            // If the text to convert is short enough, attempt conversion without
            // requiring user permission and skip the dialog if no conversion was made.
            return
        }

        if (!confirmConvertJavaOnPaste(project, isPlainText = false)) return

        val conversionTime = measureTimeMillis { converter.convert() }
        J2KFusCollector.log(
            type = ConversionType.PSI_EXPRESSION,
            isNewJ2k = j2kKind == K1_NEW,
            conversionTime,
            linesCount = conversionData.elementsAndTexts.lineCount(),
            filesCount = 1
        )

        Util.conversionPerformed = true
    }

    object Util {
        @get:TestOnly
        var conversionPerformed: Boolean = false
    }
}
