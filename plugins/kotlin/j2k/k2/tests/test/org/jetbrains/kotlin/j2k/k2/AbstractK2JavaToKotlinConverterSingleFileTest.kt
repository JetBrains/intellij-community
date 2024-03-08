// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.k2

import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.j2k.AbstractJavaToKotlinConverterSingleFileTest
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.J2kConverterExtension
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K2

abstract class AbstractK2JavaToKotlinConverterSingleFileTest : AbstractJavaToKotlinConverterSingleFileTest() {
    override fun isFirPlugin(): Boolean = true

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() }
        )
    }

    override fun fileToKotlin(text: String, settings: ConverterSettings): String {
        val file = createJavaFile(text)
        val extension = J2kConverterExtension.extension(K2)
        val converter = extension.createJavaToKotlinConverter(project, module, settings)
        val postProcessor = extension.createPostProcessor()
        return converter.filesToKotlin(listOf(file), postProcessor).results.single()
    }
}