// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.idea.j2k.post.processing.NewJ2kPostProcessor
import org.jetbrains.kotlin.j2k.AbstractJavaToKotlinConverterSingleFileTest
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.types.FlexibleTypeImpl
import java.io.File

abstract class AbstractNewJavaToKotlinConverterSingleFileTest : AbstractJavaToKotlinConverterSingleFileTest() {
    override fun doTest(javaPath: String) {
        // TODO KTIJ-5630 (K1 only)
        FlexibleTypeImpl.RUN_SLOW_ASSERTIONS = !javaPath.endsWith("typeParameters/rawTypeCast.java")

        super.doTest(javaPath)
    }

    override fun fileToKotlin(file: PsiJavaFile, settings: ConverterSettings): String {
        return NewJavaToKotlinConverter(project, module, settings)
            .filesToKotlin(listOf(file), NewJ2kPostProcessor()).results.single()
    }

    override fun provideExpectedFile(javaPath: String): File =
        File(javaPath.replace(".java", ".new.kt")).takeIf { it.exists() }
            ?: super.provideExpectedFile(javaPath)
}
