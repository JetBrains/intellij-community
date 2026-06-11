// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.terminal

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.terminal.pyTerminalDefaultWorkingDirectory
import com.intellij.python.test.env.junit5.pyVenvFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.PyNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

/**
 * [pyTerminalDefaultWorkingDirectory] should point a new terminal at the content root of the **Python**
 * module that owns the currently opened file, regardless of whether that module has a Python SDK (PY-90039).
 * Files in non-Python modules are skipped so the default terminal working directory is kept.
 */
@PyEnvTestCase
class PyTerminalDefaultWorkingDirectoryTest {
  private val projectFixture = projectFixture()

  // A module backed by its own venv.
  private val moduleWithSdkDir = tempPathFixture(prefix = "subproject_with_venv")
  private val moduleWithSdk = projectFixture.moduleFixture(moduleWithSdkDir, addPathToSourceRoot = true,
                                                           moduleTypeId = PyNames.PYTHON_MODULE_ID)

  @Suppress("unused") // creates the venv and assigns it to moduleWithSdk
  private val venvFixture = pySdkFixture().pyVenvFixture(
    where = moduleWithSdkDir,
    addToSdkTable = true,
    moduleFixture = moduleWithSdk,
  )

  // A module without any Python SDK.
  private val moduleWithoutSdkDir = tempPathFixture(prefix = "subproject_without_venv")
  private val moduleWithoutSdk = projectFixture.moduleFixture(moduleWithoutSdkDir, addPathToSourceRoot = true,
                                                              moduleTypeId = PyNames.PYTHON_MODULE_ID)

  // A module that is not a Python module (default empty module type).
  private val nonPythonModuleDir = tempPathFixture(prefix = "subproject_non_python")
  private val nonPythonModule = projectFixture.moduleFixture(nonPythonModuleDir, addPathToSourceRoot = true)

  private suspend fun createFile(dir: Path, name: String) = withContext(Dispatchers.IO) {
    val path = dir.resolve(name)
    Files.createFile(path)
    requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path)) { "File not found in VFS: $path" }
  }

  @Test
  fun fileInModuleWithSdkUsesItsContentRoot(): Unit = timeoutRunBlocking {
    venvFixture.get() // ensure the venv is created and assigned to the module
    val dir = moduleWithSdkDir.get()
    val file = createFile(dir, "app.py")

    val result = pyTerminalDefaultWorkingDirectory(projectFixture.get(), file)
    assertEquals(dir.toRealPath(), result?.toRealPath(), "Terminal should start in the module content root")
  }

  @Test
  fun fileInModuleWithoutSdkUsesItsContentRoot(): Unit = timeoutRunBlocking {
    moduleWithoutSdk.get() // ensure the module exists
    val dir = moduleWithoutSdkDir.get()
    val file = createFile(dir, "app.py")

    val result = pyTerminalDefaultWorkingDirectory(projectFixture.get(), file)
    assertEquals(dir.toRealPath(), result?.toRealPath(),
                 "Terminal should start in the module content root even without a Python SDK")
  }

  @Test
  fun fileInNonPythonModuleIsSkipped(): Unit = timeoutRunBlocking {
    nonPythonModule.get() // ensure the module exists
    val dir = nonPythonModuleDir.get()
    val file = createFile(dir, "app.py")

    val result = pyTerminalDefaultWorkingDirectory(projectFixture.get(), file)
    assertNull(result, "Non-Python modules must be skipped so the default terminal working directory is kept")
  }

  @Test
  fun noOpenFileFallsBackToDefault(): Unit = timeoutRunBlocking {
    val result = pyTerminalDefaultWorkingDirectory(projectFixture.get(), null)
    assertNull(result, "Without an opened file the default terminal working directory must be kept")
  }

  /**
   * Regression test for IDEA-390402: [pyTerminalDefaultWorkingDirectory] is invoked synchronously while
   * loading run configurations on startup, where a write action may be in progress on another thread.
   * It must never block to acquire the read lock there (the previous `runReadActionBlocking` implementation
   * deadlocked the IDE on startup). It reads the lock-free `WorkspaceModel` snapshot, so it returns promptly —
   * and still resolves the content root — even while a write action is held.
   */
  @Test
  fun doesNotBlockWhileWriteActionIsRunning(): Unit = timeoutRunBlocking(60.seconds) {
    moduleWithoutSdk.get() // ensure the module exists
    val dir = moduleWithoutSdkDir.get()
    val file = createFile(dir, "app.py")
    val project = projectFixture.get()

    val writeHeld = CountDownLatch(1)
    val releaseWrite = CountDownLatch(1)
    val callFinished = CountDownLatch(1)
    val result = AtomicReference<Path?>()

    // Hold the global write lock until we release it, mimicking a write action in progress during startup.
    val writer = thread(name = "py-terminal-write-action-holder") {
      WriteAction.runAndWait<Throwable> {
        writeHeld.countDown()
        releaseWrite.await()
      }
    }
    // Call the function under test from another thread while the write action is held.
    val caller = thread(name = "py-terminal-default-working-dir-caller") {
      writeHeld.await()
      result.set(pyTerminalDefaultWorkingDirectory(project, file))
      callFinished.countDown()
    }

    try {
      // With the fix the call returns promptly; the previous blocking read action would hang here
      // until the write action completes, so this await would time out.
      val finishedInTime = callFinished.await(10, TimeUnit.SECONDS)
      assertTrue(finishedInTime,
                 "pyTerminalDefaultWorkingDirectory must not block while a write action is in progress (IDEA-390402)")
      assertEquals(dir.toRealPath(), result.get()?.toRealPath(),
                   "The lock-free snapshot lookup must still resolve the module content root under a write action")
    }
    finally {
      releaseWrite.countDown()
      writer.join()
      caller.join()
    }
  }
}
