// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.codevision

import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.utils.codeVision.CodeVisionTestUtil
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.TestMetadataUtil
import org.junit.jupiter.api.BeforeEach
import java.io.File

abstract class AbstractKotlinCodeVisionProviderTest { // Abstract- prefix is just a convention for GenerateTests
    companion object {
        private val projectFixture = projectFixture(openAfterCreation = true)
    }

    private val myFixture by codeInsightModuleFixture(projectFixture)

    @BeforeEach
    fun setUp() {
        CodeVisionTestUtil.setUp(myFixture)
        ConfigLibraryUtil.configureKotlinRuntime(myFixture.module)
    }

    /**
     * Runs [doTest] for [testDataFilePath] relative to the `@TestRoot` of the generated class.
     * The generated `@Test` methods call it.
     */
    protected fun runTest(testDataFilePath: String) {
        val testRoot = TestMetadataUtil.getTestRoot(javaClass) ?: error("@TestRoot annotation was not found on ${javaClass.name}")
        doTest(File(testRoot, testDataFilePath).path)
    }

    fun doTest(testPath: String) { // named according to the convention imposed by GenerateTests
        assertThatActualHintsMatch(testPath)
    }

    private fun assertThatActualHintsMatch(fileName: String) {
        val file = File(fileName)
        val fileContents = FileUtil.loadFile(file, true)

        val mode = when (InTextDirectivesUtils.findStringWithPrefixes(fileContents, "// MODE: ")) {
            "inheritors" -> arrayOf("inheritors")
            "usages" -> arrayOf("references")
            "usages-&-inheritors" -> arrayOf("references", "inheritors")
            else -> emptyArray()
        }

        CodeVisionTestUtil.testProviders(myFixture, fileContents, file.name, *mode)
    }
}