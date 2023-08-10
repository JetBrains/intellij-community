// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiJavaFile

object JavaToKotlinTranslator {
    private fun createFile(text: String, project: Project): PsiFile? {
        return PsiFileFactory.getInstance(project).createFileFromText("test.java", JavaLanguage.INSTANCE, text)
    }

    private fun prettify(code: String?): String {
        if (code == null) {
            return ""
        }

        return code
                .trim()
                .replace("\r\n", "\n")
                .replace(" \n", "\n")
                .replace("\n ", "\n")
                .replace("\n+".toRegex(), "\n")
                .replace(" +".toRegex(), " ")
                .trim()
    }

    fun generateKotlinCode(javaCode: String, project: Project): String {
        val file = createFile(javaCode, project)
        if (file is PsiJavaFile) {
            val converter = OldJavaToKotlinConverter(file.project, ConverterSettings.defaultSettings, EmptyJavaToKotlinServices)
            return prettify(converter.elementsToKotlin(listOf(file)).results.single()!!.text) //TODO: imports
        }
        return ""
    }
}

//used in Kotlin Web Demo
fun translateToKotlin(code: String, project: Project): String {
    return JavaToKotlinTranslator.generateKotlinCode(code, project)
}
