// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.maven

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.KotlinRoot
import org.junit.Test
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File
import kotlin.reflect.KMutableProperty0

@RunWith(JUnit38ClassRunner::class)
class MavenUpdateConfigurationQuickFixTest12 : KotlinMavenImportingTestCase() {
    private lateinit var codeInsightTestFixture: CodeInsightTestFixture

    private fun getTestDataPath(): String {
        return KotlinRoot.DIR.resolve("maven/tests/testData/languageFeature").resolve(getTestName(true)).path.substringBefore('_')
    }

    override fun setUpFixtures() {
        myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name).fixture
        codeInsightTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myTestFixture)
        codeInsightTestFixture.setUp()
    }

    override fun tearDownFixtures() {
        runAll(
            ThrowableRunnable { codeInsightTestFixture.tearDown() },
            ThrowableRunnable {
                @Suppress("UNCHECKED_CAST")
                (this::codeInsightTestFixture as KMutableProperty0<CodeInsightTestFixture?>).set(null)
            },
            ThrowableRunnable { myTestFixture = null }
        )
    }

    @Test
    fun testUpdateLanguageVersion() {
        doTest("Set module language version to 1.1")
    }

    @Test
    fun testUpdateLanguageVersionProperty() {
        doTest("Set module language version to 1.1")
    }

    @Test
    fun testUpdateApiVersion() {
        doTest("Set module API version to 1.1")
    }

    @Test
    fun testUpdateLanguageAndApiVersion() {
        doTest("Set module language version to 1.1")
    }

    @Test
    fun testEnableInlineClasses() {
        doTest("Enable inline classes support in the current module")
    }

    @Test
    fun testEnableInlineClassesWithXFlag() {
        doTest("Enable inline classes support in the current module")
    }

    @Test
    fun testAddKotlinReflect() {
        doTest("Add 'kotlin-reflect.jar' to the classpath")
    }

    private fun doTest(intentionName: String) {
        val pomVFile = createProjectSubFile("pom.xml", File(getTestDataPath(), "pom.xml").readText())
        val sourceVFile = createProjectSubFile("src/main/kotlin/src.kt", File(getTestDataPath(), "src.kt").readText())
        myProjectPom = pomVFile
        myAllPoms.add(myProjectPom)
        importProject()
        runInEdtAndWait {
            assertTrue(ModuleRootManager.getInstance(myTestFixture.module).fileIndex.isInSourceContent(sourceVFile))
            codeInsightTestFixture.configureFromExistingVirtualFile(sourceVFile)
            codeInsightTestFixture.launchAction(codeInsightTestFixture.findSingleIntention(intentionName))
            FileDocumentManager.getInstance().saveAllDocuments()
            checkResult(pomVFile)
        }
    }

    private fun checkResult(file: VirtualFile) {
        val expectedPath = File(getTestDataPath(), "pom.xml.after")
        val expectedContent = FileUtil.loadFile(expectedPath, true)
        val actualContent = LoadTextUtil.loadText(file).toString()
        if (actualContent != expectedContent) {
            throw FileComparisonFailure("pom.xml doesn't match", expectedContent, actualContent, expectedPath.path)
        }
    }
}