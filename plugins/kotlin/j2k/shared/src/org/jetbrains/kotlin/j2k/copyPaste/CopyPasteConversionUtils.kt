// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.copyPaste

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider.Companion.isK2Mode
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.configuration.ExperimentalFeatures.NewJ2k
import org.jetbrains.kotlin.idea.editor.KotlinEditorOptions
import org.jetbrains.kotlin.j2k.*
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K1_NEW
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K1_OLD
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K2
import org.jetbrains.kotlin.j2k.ParseContext.CODE_BLOCK
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.nj2k.KotlinNJ2KBundle
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

data class ConversionResult(
    val text: String,
    val parseContext: ParseContext,
    val importsToAdd: Set<FqName>,
    val isTextChanged: Boolean,
    val converterContext: ConverterContext?
)

fun ElementAndTextList.convertCodeToKotlin(
    project: Project,
    targetFile: KtFile,
    j2kKind: J2kConverterExtension.Kind
): ConversionResult {
    val converter = J2kConverterExtension.extension(j2kKind).createJavaToKotlinConverter(
        project,
        targetFile.module,
        ConverterSettings.defaultSettings,
        targetFile
    )

    val inputElements = this.toList().filterIsInstance<PsiElement>()
    val (results, _, converterContext) = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        ThrowableComputable {
            runReadAction { converter.elementsToKotlin(inputElements) }
        },
        KotlinNJ2KBundle.message("copy.text.convert.java.to.kotlin.title"),
        true,
        project
    )

    val importsToAdd = mutableSetOf<FqName>()
    val convertedCodeBuilder = StringBuilder()
    val originalCodeBuilder = StringBuilder()
    var parseContext: ParseContext? = null
    var resultIndex = 0

    this.process(object : ElementsAndTextsProcessor {
        override fun processElement(element: PsiElement) {
            val originalText = element.text
            originalCodeBuilder.append(originalText)

            val result = results[resultIndex]
            resultIndex++

            if (result != null) {
                convertedCodeBuilder.append(result.text.trimEnd('\n'))
                if (parseContext == null) {
                    // Use the parse context of the first converted element as a parse context for the whole text
                    parseContext = result.parseContext
                }
                importsToAdd.addAll(result.importsToAdd)
            } else {
                // Failed to convert the element to Kotlin, insert the Java text as is
                convertedCodeBuilder.append(originalText)
            }
        }

        override fun processText(text: String) {
            originalCodeBuilder.append(text)
            convertedCodeBuilder.append(text)
        }
    })

    val convertedCode = convertedCodeBuilder.toString()
    val originalCode = originalCodeBuilder.toString()
    val textChanged = convertedCode != originalCode
    return ConversionResult(
        convertedCode,
        parseContext ?: CODE_BLOCK,
        importsToAdd,
        textChanged,
        converterContext
    )
}

/**
 * Does it make sense to run J2K on pasted code in this particular place in the file?
 */
fun isConversionSupportedAtPosition(file: KtFile, offset: Int): Boolean {
    if (offset == 0) return true
    val token = file.findElementAt(offset - 1) ?: return false

    if (token !is PsiWhiteSpace && token.endOffset != offset) {
        // pasting into the middle of a token
        return false
    }

    for (element in token.parentsWithSelf) {
        when (element) {
            is PsiComment -> return element.node.elementType != KtTokens.EOL_COMMENT && offset == element.endOffset
            is KtStringTemplateEntryWithExpression -> return true
            is KtStringTemplateExpression -> return false
        }
    }

    return true
}

fun confirmConvertJavaOnPaste(project: Project, isPlainText: Boolean): Boolean {
    if (KotlinEditorOptions.getInstance().isDonTShowConversionDialog) return true
    val dialog = KotlinPasteFromJavaDialog(project, isPlainText)
    dialog.show()
    return dialog.isOK
}

fun ElementAndTextList.lineCount(): Int {
    val elements = this.toList().filterIsInstance<PsiElement>()
    return elements.sumOf { StringUtil.getLineBreakCount(it.text) }
}

fun getJ2kKind(targetFile: KtFile): J2kConverterExtension.Kind = when {
    isK2Mode() -> K2
    targetFile is KtCodeFragment || !NewJ2k.isEnabled -> K1_OLD
    else -> K1_NEW
}

fun runPostProcessing(
    project: Project,
    file: KtFile,
    bounds: TextRange?,
    converterContext: ConverterContext?,
    j2kKind: J2kConverterExtension.Kind
) {
    val postProcessor = J2kConverterExtension.extension(j2kKind).createPostProcessor()
    if (j2kKind != K1_OLD) {
        val runnable = {
            val processor = J2kConverterExtension.extension(j2kKind).createWithProgressProcessor(
                ProgressManager.getInstance().progressIndicator!!,
                emptyList(),
                postProcessor.phasesCount
            )
            J2KPostProcessingRunner.run(postProcessor, file, converterContext, bounds) { phase, description ->
                processor.updateState(0, phase, description)
            }
        }
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            runnable,
            KotlinNJ2KBundle.message("copy.text.convert.java.to.kotlin.title"),
            /* canBeCanceled = */ true,
            project
        )
    } else {
        J2KPostProcessingRunner.run(postProcessor, file, converterContext, bounds)
    }
}
