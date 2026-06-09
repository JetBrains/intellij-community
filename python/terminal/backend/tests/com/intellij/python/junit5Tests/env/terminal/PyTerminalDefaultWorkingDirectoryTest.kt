// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.terminal

import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.terminal.pyTerminalDefaultWorkingDirectory
import com.intellij.python.test.env.junit5.pyVenvFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * [pyTerminalDefaultWorkingDirectory] should point a new terminal at the content root of the module
 * that owns the currently opened file, regardless of whether that module has a Python SDK (PY-90039).
 */
@PyEnvTestCase
class PyTerminalDefaultWorkingDirectoryTest {
  private val projectFixture = projectFixture()

  // A module backed by its own venv.
  private val moduleWithSdkDir = tempPathFixture(prefix = "subproject_with_venv")
  private val moduleWithSdk = projectFixture.moduleFixture(moduleWithSdkDir, addPathToSourceRoot = true)

  @Suppress("unused") // creates the venv and assigns it to moduleWithSdk
  private val venvFixture = pySdkFixture().pyVenvFixture(
    where = moduleWithSdkDir,
    addToSdkTable = true,
    moduleFixture = moduleWithSdk,
  )

  // A module without any Python SDK.
  private val moduleWithoutSdkDir = tempPathFixture(prefix = "subproject_without_venv")
  private val moduleWithoutSdk = projectFixture.moduleFixture(moduleWithoutSdkDir, addPathToSourceRoot = true)

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
  fun noOpenFileFallsBackToDefault(): Unit = timeoutRunBlocking {
    val result = pyTerminalDefaultWorkingDirectory(projectFixture.get(), null)
    assertNull(result, "Without an opened file the default terminal working directory must be kept")
  }
}
