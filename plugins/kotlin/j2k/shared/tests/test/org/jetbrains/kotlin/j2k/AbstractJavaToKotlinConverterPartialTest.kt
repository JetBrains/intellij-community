// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K1_NEW
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K2
import org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverter

abstract class AbstractJavaToKotlinConverterPartialTest : AbstractJavaToKotlinConverterSingleFileTest() {
    override fun fileToKotlin(text: String, settings: ConverterSettings, preprocessorExtensions: List<J2kPreprocessorExtension>, postprocessorExtensions: List<J2kPostprocessorExtension>): String {
        val file = createJavaFile(text)
        val element = myFixture.elementAtCaret

        val j2kKind = if (KotlinPluginModeProvider.isK2Mode()) K2 else K1_NEW
        val extension = J2kConverterExtension.extension(j2kKind)
        val postProcessor = extension.createPostProcessor()

        var converterResult: FilesResult? = null
        val process = {
            converterResult = NewJavaToKotlinConverter(project, module, settings).filesToKotlin(
                listOf(file),
                postProcessor,
                EmptyProgressIndicator(),
                { it == element }, preprocessorExtensions = emptyList(), postprocessorExtensions = emptyList()
            )
        }

        project.executeCommand("J2K") {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(process, "Testing J2K", /* canBeCanceled = */ true, project)
        }
        return converterResult!!.results.single()
    }
}
