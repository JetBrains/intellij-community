// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.psi.PsiElement

abstract class AbstractJavaToKotlinConverterPartialTest : AbstractJavaToKotlinConverterSingleFileTest() {
    override fun fileToKotlin(
        text: String,
        settings: ConverterSettings,
        bodyFilter: ((PsiElement) -> Boolean)?,
        preprocessorExtensions: List<J2kPreprocessorExtension>,
        postprocessorExtensions: List<J2kPostprocessorExtension>
    ): String {
        return super.fileToKotlin(text, settings, { it == myFixture.elementAtCaret }, emptyList(), emptyList())
    }
}
