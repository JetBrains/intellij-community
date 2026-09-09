// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.InitialVfsRefreshService
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.api.ModelRebuiltListener
import com.intellij.python.pyproject.model.api.isPyProjectTomlBased
import com.intellij.python.pyproject.model.internal.MODEL_REBUILD
import com.intellij.python.pyproject.model.internal.platformBridge.PyProjectModelSyncService
import com.intellij.python.pyproject.model.spi.PyProjectManager
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.io.createDirectories
import com.jetbrains.python.venvReader.Directory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.apache.tuweni.toml.TomlTable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Drives [PyProjectModelSyncService.start] and then changes the tree.
 *
 * `PyProjectModelSyncServiceTest` calls the rebuild directly, so it covers no event. This test covers the VFS
 * listener, the debounce and the subtree load of PY-91841. Each case writes with `java.nio` and refreshes one
 * directory without recursion, because that is how a change of an external tool reaches the IDE.
 */
@TestApplication
@TestFor(issues = ["PY-91841"])
internal class PyProjectTomlVfsListenerTest {
  private val projectPathFixture = tempPathFixture()
  private val outsidePathFixture = tempPathFixture()
  private val projectFixture = projectFixture(projectPathFixture)

  private lateinit var root: Path

  @BeforeEach
  fun startSync(): Unit = timeoutRunBlocking(TEST_TIMEOUT) {
    root = projectPathFixture.get()
    root.resolve("member").writeToml("member")
    val project = projectFixture.get()
    // The scanning pass is what completes this deferred, and no scan runs in a test. The service waits for it,
    // so the test takes the place of `UnindexedFilesScanner`. In a test the call completes the wait at once.
    project.service<InitialVfsRefreshService>().scheduleInitialVfsRefresh()
    project.service<PyProjectModelSyncService>().start()
    awaitPyModules("the first build after start", "member")
  }

  @Test
  fun testNestedTomlOfANewDirectory(): Unit = timeoutRunBlocking(TEST_TIMEOUT) {
    // The VFS learns about `fresh`, and it loads no grandchild. Only the subtree load finds `fresh/nested`.
    root.resolve("fresh").resolve("nested").writeToml("nested")
    refreshWithoutRecursion(root)
    awaitPyModules("a nested pyproject.toml of a new directory", "member", "nested")
  }

  @Test
  fun testDirectoryMovedIntoTheProject(): Unit = timeoutRunBlocking(TEST_TIMEOUT) {
    // The old parent lies outside every root, so only the new parent can keep the event.
    val outside = outsidePathFixture.get()
    outside.resolve("guest").writeToml("guest")
    val guest = findInVfs(outside.resolve("guest"))
    val rootDirectory = findInVfs(root)
    writeAction { guest.move(this, rootDirectory) }
    awaitPyModules("a directory moved in from outside the project", "member", "guest")
  }

  @Test
  fun testDirectoryRenamed(): Unit = timeoutRunBlocking(TEST_TIMEOUT) {
    val member = findInVfs(root.resolve("member"))
    writeAction { member.rename(this, "renamed") }
    // The module set never changes here, so only the content root proves that the rename reached the model.
    // The name is not asserted. `assignNames` reserves every current module name to keep the `.iml` rename
    // bridge safe (PY-89055), so a directory rename makes the module `member@1`. That behaviour predates
    // PY-91841 and this test must not pin it.
    awaitValue("a renamed directory", listOf("renamed")) { pyModuleContentRootNames() }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun testRebuildAfterFailure(onStart: Boolean, @TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(TEST_TIMEOUT) {
    val service = projectFixture.get().service<PyProjectModelSyncService>()
    if (onStart) service.stop()
    val managers = PyProjectManager.EP.extensionList
    val manager = managers.first()
    val fail = AtomicBoolean(true)
    val failed = CompletableDeferred<Unit>()
    val failingManager = object : PyProjectManager by manager {
      override suspend fun getSrcRoots(toml: TomlTable, projectRoot: Directory): Set<Directory> {
        if (fail.get()) {
          failed.complete(Unit)
          throw IOException("Cannot read the source roots")
        }
        return manager.getSrcRoots(toml, projectRoot)
      }
    }
    ExtensionTestUtil.maskExtensions(PyProjectManager.EP, listOf(failingManager) + managers.drop(1), disposable)

    if (onStart) {
      service.start()
    }
    else {
      root.resolve("member").writeToml("changed")
      refreshWithoutRecursion(root.resolve("member"))
    }
    failed.await()
    fail.set(false)
    root.resolve("second").writeToml("second")
    refreshWithoutRecursion(root)
    awaitPyModules("a change after a failed rebuild", if (onStart) "member" else "changed", "second")
  }

  @Test
  fun testTomlDeleted(): Unit = timeoutRunBlocking(TEST_TIMEOUT) {
    root.resolve("member").resolve(PY_PROJECT_TOML).deleteExisting()
    refreshWithoutRecursion(root.resolve("member"))
    awaitPyModules("a deleted pyproject.toml")
  }

  @Test
  fun testDirectoryDeleted(): Unit = timeoutRunBlocking(TEST_TIMEOUT) {
    val member = findInVfs(root.resolve("member"))
    writeAction { member.delete(this) }
    awaitPyModules("a deleted directory")
  }

  /**
   * One change must cost a bounded number of rebuilds.
   *
   * A rebuild writes an `.iml` file and an exclusion of its own, so it can wake the VFS listener and the
   * workspace model tracker again. A loop would keep the CPU busy for the life of the project.
   */
  @Test
  fun testOneChangeCostsFewRebuilds(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(TEST_TIMEOUT) {
    delay(QUIET) // Let the build of `startSync` settle, so only the change below can cause a rebuild.
    val rebuilds = AtomicInteger()
    projectFixture.get().messageBus.connect(disposable)
      .subscribe(MODEL_REBUILD, ModelRebuiltListener { rebuilds.incrementAndGet() })

    root.resolve("second").writeToml("second")
    refreshWithoutRecursion(root)
    awaitPyModules("a second member", "member", "second")

    delay(QUIET)
    assertTrue(rebuilds.get() <= MAX_REBUILDS_PER_CHANGE,
               "One change caused ${rebuilds.get()} rebuilds, which looks like a loop")
  }

  @Test
  fun testTomlContentChanged(): Unit = timeoutRunBlocking(TEST_TIMEOUT) {
    root.resolve("member").writeToml("newName")
    refreshWithoutRecursion(root.resolve("member"))
    awaitPyModules("a new project name in the pyproject.toml", "newName")
  }

  /** Writes a `pyproject.toml` that names one project. */
  private fun Path.writeToml(name: String): Path {
    createDirectories()
    val toml = resolve(PY_PROJECT_TOML)
    toml.writeText(
      """
      [project]
      name = "$name"
      version = "1.0"
      """.trimIndent()
    )
    return toml
  }

  private fun findInVfs(path: Path): VirtualFile =
    VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path) ?: error("The VFS cannot find $path")

  /**
   * Makes the VFS see a change of [directory] itself, without recursion.
   *
   * This is the shape of a real event. The VFS learns about a new child, and it loads no grandchild.
   */
  private fun refreshWithoutRecursion(directory: Path) {
    findInVfs(directory).refresh(false, false)
  }

  /** The name of every `pyproject.toml` based module. */
  private fun pyModuleNames(): List<String> =
    projectFixture.get().modules.filter { it.isPyProjectTomlBased }.map { it.name }.sorted()

  /** The content root name of every `pyproject.toml` based module. */
  private fun pyModuleContentRootNames(): List<String> =
    projectFixture.get().modules.filter { it.isPyProjectTomlBased }
      .flatMap { ModuleRootManager.getInstance(it).contentRoots.map { root -> root.name } }
      .sorted()

  private suspend fun awaitPyModules(what: String, vararg expected: String) {
    awaitValue(what, expected.sorted()) { pyModuleNames() }
  }

  /**
   * Waits until [actual] returns [expected].
   *
   * A rebuild is asynchronous and the listener debounces, so a poll is the only stable wait. A count of the
   * rebuild events would also see a rebuild that another change caused.
   */
  private suspend fun <T> awaitValue(what: String, expected: T, actual: () -> T) {
    val reached = withTimeoutOrNull(AWAIT_MODULES) {
      while (actual() != expected) {
        delay(POLL)
      }
      true
    }
    if (reached == null) {
      assertEquals(expected, actual(), "Wrong state after $what")
    }
  }
}

private val TEST_TIMEOUT = 3.minutes
private val AWAIT_MODULES = 60.seconds
private val POLL = 100.milliseconds

/** Long enough for the debounce plus a rebuild that a rebuild itself started. */
private val QUIET = 3.seconds

/** The first build, plus a rebuild that the `.iml` write or the exclusion write may add. */
private const val MAX_REBUILDS_PER_CHANGE = 4
