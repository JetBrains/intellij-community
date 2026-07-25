// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import org.jetbrains.kotlin.idea.actions.withCommandOnEdt
import org.jetbrains.kotlin.nj2k.JavaToKotlinConverter

abstract class AbstractJavaToKotlinConverterPartialTest : AbstractJavaToKotlinConverterSingleFileTest() {
    override fun fileToKotlin(
        text: String,
        settings: ConverterSettings,
    ): String {
        val file = createJavaFile(text)
        val selectedElement = runReadAction { myFixture.elementAtCaret }

        val result = runWithModalProgressBlocking(project, "") {
            withCommandOnEdt(project) {
                val converter = JavaToKotlinConverter(
                    project = project,
                    targetModule = module,
                    settings = settings
                )
                converter.filesToKotlinPartiallyInTests(
                    files = listOf(element = file),
                    selectedDeclaration = selectedElement.findDeclarationToConvert(),
                )
            }
        }

        return result.kotlinCodeByJavaFile.getValue(file)
    }

}

private fun PsiElement.findDeclarationToConvert(): PsiElement =
    generateSequence(this) { it.parent }
        .firstOrNull { it is PsiClass || it is PsiField || it is PsiMethod }
        ?: error("Partial conversion supports only class, field, or method selections")
