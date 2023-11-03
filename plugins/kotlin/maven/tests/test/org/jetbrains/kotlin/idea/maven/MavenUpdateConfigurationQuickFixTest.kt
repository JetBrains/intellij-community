// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.assertWithinTimeout
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.ThrowableRunnable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.runAll
import org.junit.Assert
import org.junit.Test
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KMutableProperty0

@RunWith(JUnit38ClassRunner::class)
class MavenUpdateConfigurationQuickFixTest12 : KotlinMavenImportingTestCase() {
    private lateinit var codeInsightTestFixture: CodeInsightTestFixture

    private val artifactDownloadingScheduled = AtomicInteger()
    private val artifactDownloadingFinished = AtomicInteger()

    override fun runInDispatchThread() = false

    override fun setUp() {
        super.setUp()
        myProject.messageBus.connect(testRootDisposable)
            .subscribe(MavenImportListener.TOPIC, object : MavenImportListener {
                override fun artifactDownloadingScheduled() {
                    artifactDownloadingScheduled.incrementAndGet()
                }

                override fun artifactDownloadingFinished() {
                    artifactDownloadingFinished.incrementAndGet()
                }

                override fun importFinished(importedProjects: MutableCollection<MavenProject>, newModules: MutableList<Module>) {
                }
            })
    }

    override fun tearDown() = runBlocking {
        try {
            waitForScheduledArtifactDownloads()
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    private suspend fun waitForScheduledArtifactDownloads() {
        assertWithinTimeout {
            val scheduled = artifactDownloadingScheduled.get()
            val finished = artifactDownloadingFinished.get()
            Assert.assertEquals("Expected $scheduled artifact downloads, but finished $finished", scheduled, finished)
        }
    }

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
    fun testUpdateLanguageVersion() = runBlocking {
        doTest("Set module language version to 1.1")
    }

    @Test
    fun testUpdateLanguageVersionProperty() = runBlocking {
        doTest("Set module language version to 1.1")
    }

    @Test
    fun testUpdateApiVersion() = runBlocking {
        doTest("Set module API version to 1.1")
    }

    @Test
    fun testUpdateLanguageAndApiVersion() = runBlocking {
        doTest("Set module language version to 1.1")
    }

    @Test
    fun testEnableInlineClasses() = runBlocking {
        doTest("Enable inline classes support in the current module")
    }

    @Test
    fun testEnableInlineClassesWithXFlag() = runBlocking {
        doTest("Enable inline classes support in the current module")
    }

    @Test
    fun testAddKotlinReflect() = runBlocking {
        doTestAndWaitForMavenImport("Add 'kotlin-reflect.jar' to the classpath")
    }

    private suspend fun doTest(intentionName: String) {
        val pomVFile = createProjectSubFile("pom.xml", File(getTestDataPath(), "pom.xml").readText())
        val sourceVFile = createProjectSubFile("src/main/kotlin/src.kt", File(getTestDataPath(), "src.kt").readText())
        myProjectPom = pomVFile
        myAllPoms.add(myProjectPom)
        importProjectAsync()
        withContext(Dispatchers.EDT) {
            assertTrue(ModuleRootManager.getInstance(myTestFixture.module).fileIndex.isInSourceContent(sourceVFile))
            codeInsightTestFixture.configureFromExistingVirtualFile(sourceVFile)
            codeInsightTestFixture.launchAction(codeInsightTestFixture.findSingleIntention(intentionName))
            FileDocumentManager.getInstance().saveAllDocuments()
            checkResult(pomVFile)
        }
    }

    private suspend fun doTestAndWaitForMavenImport(intentionName: String) {
        val pomVFile = createProjectSubFile("pom.xml", File(getTestDataPath(), "pom.xml").readText())
        val sourceVFile = createProjectSubFile("src/main/kotlin/src.kt", File(getTestDataPath(), "src.kt").readText())
        myProjectPom = pomVFile
        myAllPoms.add(myProjectPom)
        importProjectAsync()
        withContext(Dispatchers.EDT) {
            assertTrue(ModuleRootManager.getInstance(myTestFixture.module).fileIndex.isInSourceContent(sourceVFile))
        }
        codeInsightTestFixture.configureFromExistingVirtualFile(sourceVFile)
        val intentionAction = codeInsightTestFixture.findSingleIntention(intentionName)
        waitForImportWithinTimeout {
            withContext(Dispatchers.EDT) {
                intentionAction.invoke(myProject, codeInsightTestFixture.editor, codeInsightTestFixture.file)
            }
        }
        withContext(Dispatchers.EDT) {
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