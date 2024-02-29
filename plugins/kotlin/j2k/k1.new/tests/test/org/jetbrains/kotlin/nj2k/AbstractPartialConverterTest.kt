// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.progress.EmptyProgressIndicator
import org.jetbrains.kotlin.idea.j2k.post.processing.NewJ2kPostProcessor
import org.jetbrains.kotlin.j2k.ConverterSettings

abstract class AbstractPartialConverterTest : AbstractNewJavaToKotlinConverterSingleFileTest() {
    override fun fileToKotlin(text: String, settings: ConverterSettings): String {
        val file = createJavaFile(text)
        val element = myFixture.elementAtCaret
        return NewJavaToKotlinConverter(project, module, settings).filesToKotlin(
            listOf(file),
            NewJ2kPostProcessor(),
            EmptyProgressIndicator()
        ) { it == element }.results.single()
    }
}
