// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.j2k.IdeaJavaToKotlinServices
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.j2k.AbstractJavaToKotlinConverterSingleFileTest
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.nj2k.postProcessing.NewJ2kPostProcessor
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.File

abstract class AbstractNewJavaToKotlinConverterSingleFileTest : AbstractJavaToKotlinConverterSingleFileTest() {
    override fun doTest(javaPath: String) {
        val javaFile = File(javaPath)
        withCustomCompilerOptions(javaFile.readText(), project, module) {
            val directory = javaFile.parentFile
            val expectedFileName = "${javaFile.nameWithoutExtension}.external"
            val expectedFiles = directory.listFiles { _, name ->
                name == "$expectedFileName.kt" || name == "$expectedFileName.java"
            }!!.filterNotNull()
            for (expectedFile in expectedFiles) {
                addFile(expectedFile, dirName = null)
            }
            super.doTest(javaPath)
        }
    }

    override fun compareResults(expectedFile: File, actual: String) {
        KotlinTestUtils.assertEqualsToFile(expectedFile, actual)
    }

    override fun setUp() {
        super.setUp()
        JavaCodeStyleSettings.getInstance(project).USE_EXTERNAL_ANNOTATIONS = true
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { JavaCodeStyleSettings.getInstance(project).USE_EXTERNAL_ANNOTATIONS = false },
            ThrowableRunnable { super.tearDown() }
        )
    }

    override fun fileToKotlin(text: String, settings: ConverterSettings, project: Project): String {
        val file = createJavaFile(text)
        return NewJavaToKotlinConverter(project, module, settings, IdeaJavaToKotlinServices)
            .filesToKotlin(listOf(file), NewJ2kPostProcessor()).results.single()
    }

    override fun provideExpectedFile(javaPath: String): File =
        File(javaPath.replace(".java", ".new.kt")).takeIf { it.exists() }
            ?: super.provideExpectedFile(javaPath)

    private fun getLanguageLevel() =
        if (testDataDirectory.toString().contains("newJavaFeatures")) LanguageLevel.HIGHEST else LanguageLevel.JDK_1_8

    override fun getProjectDescriptor() = descriptorByFileDirective(File(testDataPath, fileName()), languageLevel = getLanguageLevel())
}
