// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import org.jetbrains.kotlin.idea.actions.withCommandOnEdt

abstract class AbstractJavaToKotlinConverterPartialTest : AbstractJavaToKotlinConverterSingleFileTest() {
    override fun fileToKotlin(
        text: String,
        settings: ConverterSettings,
        preprocessorExtensions: List<J2kPreprocessorExtension>,
        postprocessorExtensions: List<J2kPostprocessorExtension>
    ): String {
        val file = createJavaFile(text)
        val selectedElement = runReadAction { myFixture.elementAtCaret }

        val result = runWithModalProgressBlocking(project, "") {
            withCommandOnEdt(project) {
                convertJavaFilesToKotlinPartially(
                    file = file,
                    selectedElement = selectedElement,
                    settings = settings,
                    preprocessorExtensions = emptyList(),
                    postprocessorExtensions = emptyList(),
                )
            }
        }

        return result.kotlinCodeByJavaFile.getValue(file)
    }

    protected fun convertJavaFileToKotlinPartially(
        text: String,
        settings: ConverterSettings,
        preprocessorExtensions: List<J2kPreprocessorExtension> = J2kPreprocessorExtension.EP_NAME.extensionList,
        postprocessorExtensions: List<J2kPostprocessorExtension> = J2kPostprocessorExtension.EP_NAME.extensionList,
    ): InMemoryConversionSnapshot {
        val file = createJavaFile(text)
        val selectedElement = runReadAction { myFixture.elementAtCaret }
        val result = runWithModalProgressBlocking(project, "") {
            withCommandOnEdt(project) {
                convertJavaFilesToKotlinPartially(
                    file = file,
                    selectedElement = selectedElement,
                    settings = settings,
                    preprocessorExtensions = preprocessorExtensions,
                    postprocessorExtensions = postprocessorExtensions,
                )
            }
        }
        return InMemoryConversionSnapshot(file.text, result.kotlinCodeByJavaFile.getValue(file), result.externalCodeProcessing)
    }

    protected fun convertJavaFileToKotlinPartiallyWithTestPreprocessor(
        text: String,
        settings: ConverterSettings,
    ): InMemoryConversionSnapshot = convertJavaFileToKotlinPartially(
        text,
        settings,
        preprocessorExtensions = listOf(J2kTestPreprocessorExtension),
    )

    private suspend fun convertJavaFilesToKotlinPartially(
        file: PsiJavaFile,
        selectedElement: PsiElement,
        settings: ConverterSettings,
        preprocessorExtensions: List<J2kPreprocessorExtension>,
        postprocessorExtensions: List<J2kPostprocessorExtension>,
    ): ConversionResult {
        val j2kConverterExtension = J2kConverterExtension.extension()
        val converter = j2kConverterExtension.createJavaToKotlinConverter(project, module, settings)
        val postProcessor = j2kConverterExtension.createPostProcessor()
        return converter.filesToKotlinPartiallyInTests(
            files = listOf(file),
            postProcessor = postProcessor,
            selectedDeclaration = selectedElement.findDeclarationToConvert(),
            preprocessorExtensions = preprocessorExtensions,
            postprocessorExtensions = postprocessorExtensions,
        )
    }
}

private fun PsiElement.findDeclarationToConvert(): PsiElement =
    generateSequence(this) { it.parent }
        .firstOrNull { it is PsiClass || it is PsiField || it is PsiMethod }
        ?: error("Partial conversion supports only class, field, or method selections")
