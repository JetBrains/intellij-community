// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.assertWithinTimeout
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.refreshFiles
import com.intellij.maven.testFramework.fixtures.setupJdkForModule
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.utils.io.deleteRecursively
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.TestFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/**
 * JUnit 5 base for Kotlin Maven CodeInsight tests (highlighting / quick-fixes / multi-file), replacing the legacy
 * JUnit 3/4 `AbstractMavenImportingTest`. Hosts a [com.intellij.testFramework.fixtures.CodeInsightTestFixture] over a
 * real Maven project via [mavenDomFixture]. Concrete tests are parameterized over Maven versions; annotate the leaf:
 * ```
 * @TestApplication
 * @ParameterizedClass
 * @ArgumentsSource(MavenVersionArguments::class)
 * class MyTest(mavenVersion: String, modelVersion: String) : AbstractMavenHighlightingTest(mavenVersion, modelVersion)
 * ```
 */
abstract class AbstractMavenImportingTest(
  mavenVersion: String,
  modelVersion: String,
) {
  protected val maven: MavenDomTestFixture by mavenDomFixture(mavenVersion = mavenVersion, modelVersion = modelVersion)

  protected val project: Project get() = maven.project

  protected val codeInsightTestFixture: CodeInsightTestFixtureImpl
    get() = maven.fixture as CodeInsightTestFixtureImpl

  private val artifactDownloadingScheduled = AtomicInteger()
  private val artifactDownloadingFinished = AtomicInteger()

  private var testMethodName: String = ""

  @BeforeEach
  fun captureTestName(info: TestInfo) {
    testMethodName = info.testMethod.map { it.name }.orElse(info.displayName)
  }

  @BeforeEach
  fun subscribeToArtifactDownloading() {
    project.messageBus.connect(maven.testRootDisposable)
      .subscribe(MavenImportListener.TOPIC, object : MavenImportListener {
        override fun artifactDownloadingScheduled() {
          artifactDownloadingScheduled.incrementAndGet()
        }

        override fun artifactDownloadingFinished() {
          artifactDownloadingFinished.incrementAndGet()
        }
      })
  }

  @AfterEach
  fun waitForScheduledArtifactDownloads(): Unit = runBlocking {
    assertWithinTimeout {
      val scheduled = artifactDownloadingScheduled.get()
      val finished = artifactDownloadingFinished.get()
      Assertions.assertEquals( scheduled, finished,"Expected $scheduled artifact downloads, but finished $finished")
    }
  }

  private fun getTestName(): String = UsefulTestCase.getTestName(testMethodName, true)

  protected abstract val testRoot: String

  protected fun getTestDataPath(): String =
    KotlinRoot.DIR.resolve(testRoot).resolve(getTestName()).path.substringBefore('_')

  protected suspend fun doMultiFileTest(customAction: (suspend () -> Unit)? = null) {
    val testName = getTestName()
    val file = KotlinRoot.DIR.resolve(testRoot).resolve(testName).path + ".test"
    val parts = KotlinTestUtils.loadBeforeAfterAndDependenciesText(file)

    val (virtualFilesByName, mainFile) = configureBeforeDirectory(parts)
    val mainVirtualFile = virtualFilesByName[mainFile.name] ?: error("$mainFile not found")

    val afterDirectory = configureAfterDirectory(testName, parts)

    maven.projectPom = virtualFilesByName["pom.xml"] ?: error("'no \"pom.xml\"")
    val pomFiles = virtualFilesByName.values.filter { it.name == "pom.xml" }
    maven.importProjectsAsync(pomFiles)

    edtWriteAction {
      project.modules.forEach {
        maven.setupJdkForModule(it.name)
      }
    }

    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        assertTrue(
          // main file has to be in sources of any module
          project.modules.any {
            ModuleRootManager.getInstance(it).fileIndex.isInSourceContent(mainVirtualFile)
          }
        ,
          "${mainVirtualFile} should be in source content")

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
    FileDocumentManager.getInstance().saveAllDocuments()
    val expected = afterDirectory.refreshAndGetVirtualDirectory()
    // mavenDomFixture writes a cache-redirector settings.xml into the project dir; it is fixture infrastructure, not
    // part of the test's expected output, so exclude it from the golden directory comparison.
    PlatformTestUtil.assertDirectoriesEqual(
      expected,
      maven.projectPom.parent,
      VirtualFileFilter { it.name != "settings.xml" }
    )
  }

  protected open val beforeDirectoryPrefix: String = "before/"
  protected open val afterDirectoryPrefix: String = "after/"

  private fun configureBeforeDirectory(parts: MutableList<TestFile>): Pair<Map<String, VirtualFile>, TestFile> {
    val beforeParts = filterParts(parts, beforeDirectoryPrefix)
    val virtualFilesByName = beforeParts.associate { it.name to maven.createProjectSubFile(it.name, it.content) }
    maven.refreshFiles(virtualFilesByName.values.toList())
    val mainFile = beforeParts.singleOrNull { it.content.contains("<caret>") } ?: error("it has to be only one main file with <caret>")
    return virtualFilesByName to mainFile
  }

  private fun configureAfterDirectory(
    testName: String,
    parts: List<TestFile>
  ): Path {
    val afterDirectory = TemporaryDirectory.generateTemporaryPath("$testName.after")
    Disposer.register(maven.testRootDisposable) {
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
