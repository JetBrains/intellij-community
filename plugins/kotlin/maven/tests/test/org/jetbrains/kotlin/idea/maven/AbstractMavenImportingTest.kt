// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.assertWithinTimeout
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.utils.io.deleteRecursively
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.TestFile
import org.jetbrains.kotlin.idea.test.runAll
import org.junit.Assert
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KMutableProperty0

abstract class AbstractMavenImportingTest : KotlinMavenImportingTestCase() {
    private lateinit var _codeInsightTestFixture: CodeInsightTestFixtureImpl
    protected val codeInsightTestFixture: CodeInsightTestFixtureImpl
        get() = _codeInsightTestFixture

    private val artifactDownloadingScheduled = AtomicInteger()
    private val artifactDownloadingFinished = AtomicInteger()

    override fun setUp() {
        super.setUp()
        project.messageBus.connect(testRootDisposable)
            .subscribe(MavenImportListener.TOPIC, object : MavenImportListener {
                override fun artifactDownloadingScheduled() {
                    artifactDownloadingScheduled.incrementAndGet()
                }

                override fun artifactDownloadingFinished() {
                    artifactDownloadingFinished.incrementAndGet()
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

    protected abstract val testRoot: String

    protected fun getTestDataPath(): String =
        KotlinRoot.DIR.resolve(testRoot).resolve(getTestName(true)).path.substringBefore('_')

    override fun setUpFixtures() {
        testFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name).fixture
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(testFixture) as CodeInsightTestFixtureImpl
        fixture.setUp()
        _codeInsightTestFixture = fixture
    }

    override fun tearDownFixtures() {
        runAll(
            ThrowableRunnable { _codeInsightTestFixture.tearDown() },
            ThrowableRunnable {
                @Suppress("UNCHECKED_CAST")
                (this::_codeInsightTestFixture as KMutableProperty0<CodeInsightTestFixture?>).set(null)
            },
            ThrowableRunnable { setTestFixtureNull() }
        )
    }

    protected suspend fun doMultiFileTest(customAction: (suspend () -> Unit)? = null) {
        val testName = getTestName(true)
        val file = KotlinRoot.DIR.resolve(testRoot).resolve(testName).path + ".test"
        val parts = KotlinTestUtils.loadBeforeAfterAndDependenciesText(file)

        val (virtualFilesByName, mainFile) = configureBeforeDirectory(parts)
        val mainVirtualFile = virtualFilesByName[mainFile.name] ?: error("$mainFile not found")

        val afterDirectory = configureAfterDirectory(testName, parts)

        projectPom = virtualFilesByName["pom.xml"] ?: error("'no \"pom.xml\"")
        val pomFiles = virtualFilesByName.values.filter { it.name == "pom.xml" }
        pomFiles.forEach(::addPom)
        importProjectsAsync(pomFiles)

        edtWriteAction {
            project.modules.forEach {
                setupJdkForModule(it.name)
            }
        }

        withContext(Dispatchers.EDT) {
            writeIntentReadAction {
                assertTrue(
                    "${mainVirtualFile} should be in source content",
                    // main file has to be in sources of any module
                    project.modules.any {
                        ModuleRootManager.getInstance(it).fileIndex.isInSourceContent(mainVirtualFile)
                    }
                )

                codeInsightTestFixture.configureFromExistingVirtualFile(mainVirtualFile)
                codeInsightTestFixture.openFileInEditor(mainVirtualFile)
            }
            if (customAction == null) {
                doTestAction(mainFile)

                checkExpected(afterDirectory)
            }
        }

        if (customAction != null) {
            customAction()
            withContext(Dispatchers.EDT) {
                PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
                checkExpected(afterDirectory)
            }
        }
    }

    protected open fun checkExpected(afterDirectory: Path) {
        val expected = afterDirectory.refreshAndGetVirtualDirectory()
        PlatformTestUtil.assertDirectoriesEqual(
            expected,
            projectPom.parent
        )
    }

    protected open val beforeDirectoryPrefix: String = "before/"
    protected open val afterDirectoryPrefix: String = "after/"

    private fun configureBeforeDirectory(parts: MutableList<TestFile>): Pair<Map<String, VirtualFile>, TestFile> {
        val beforeParts = filterParts(parts, beforeDirectoryPrefix)
        val virtualFilesByName = beforeParts.associate { it.name to createProjectSubFile(it.name, it.content) }
        refreshFiles(virtualFilesByName.values.toList())
        val mainFile = beforeParts.singleOrNull { it.content.contains("<caret>") } ?: error("it has to be only one main file with <caret>")
        return virtualFilesByName to mainFile
    }

    private fun configureAfterDirectory(
        testName: String,
        parts: List<TestFile>
    ): Path {
        val afterDirectory = TemporaryDirectory.generateTemporaryPath("$testName.after")
        Disposer.register(testRootDisposable) {
            afterDirectory.deleteRecursively()
        }

        val afterParts: List<TestFile> = filterParts(parts, afterDirectoryPrefix)
        afterParts.forEach {
            val path = afterDirectory.resolve(it.name)
            Files.createDirectories(path.parent)
            Files.writeString(path, it.content)
        }

        return afterDirectory
    }

    private fun filterParts(parts: List<TestFile>, prefix: String): List<TestFile> = parts.mapNotNull {
        if (!it.name.startsWith(prefix)) return@mapNotNull null

        TestFile(it.name.removePrefix(prefix), it.content)
    }

    @RequiresEdt
    protected abstract fun doTestAction(mainFile: TestFile)
}
