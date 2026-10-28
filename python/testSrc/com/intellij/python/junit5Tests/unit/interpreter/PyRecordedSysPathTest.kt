// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.interpreter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.junit5.RegistryKeyAppLevel
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.replaceService
import com.jetbrains.python.PythonPluginDisposable
import org.junit.jupiter.api.BeforeEach
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.sdk.backend.PythonInterpreterSdks
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * The `sys.path` that the record of an SDK saves when the updater has not read it in this session, such as for an SDK of
 * the SDK table that the migration imports: its class roots from `sys.path`, and the paths transferred to its module.
 */
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "true")
@TestApplication
internal class PyRecordedSysPathTest {
  private val testDisposable by disposableFixture()

  @BeforeEach
  fun setUp() {
    // The SDK data holds its paths as pointers of the plugin disposable. This one goes with the test, and the pointers too.
    val pluginDisposable = PythonPluginDisposable()
    ApplicationManager.getApplication().replaceService(PythonPluginDisposable::class.java, pluginDisposable, testDisposable)
    Disposer.register(testDisposable, pluginDisposable)
  }

  @Test
  fun `the class roots from sys path and the transferred paths give the sys path`(@TempDir dir: Path): Unit = timeoutRunBlocking(30.seconds) {
    val sitePackages = directory(dir.resolve("venv/lib/site-packages"))
    val added = directory(dir.resolve("added"))
    val transferred = directory(dir.resolve("project/src"))
    val skeletonsPath = Path.of(PythonSdkUtil.getSkeletonsRootPath(PathManager.getSystemPath())).resolve("recorded-sys-path-test")
    val skeletons = directory(skeletonsPath)
    val sdk = PythonMockSdk.create()
    edtWriteAction {
      val modificator = sdk.sdkModificator
      modificator.removeAllRoots()
      listOf(sitePackages, skeletons, added).forEach { modificator.addRoot(it, OrderRootType.CLASSES) }
      (modificator.sdkAdditionalData as PythonSdkAdditionalData).apply {
        setAddedPathsFromVirtualFiles(setOf(added))
        setPathsToTransferFromVirtualFiles(setOf(transferred))
      }
      modificator.commitChanges()
    }

    try {
      assertThat(PythonInterpreterSdks.recordedSysPathForTests(sdk))
        .containsExactly(FileUtil.toSystemDependentName(sitePackages.path), FileUtil.toSystemDependentName(transferred.path))
    }
    finally {
      NioFiles.deleteRecursively(skeletonsPath)
    }
  }

  @Test
  fun `a sys path that the updater read wins over the roots`(): Unit = timeoutRunBlocking(30.seconds) {
    val sdk = PythonMockSdk.create()
    PythonInterpreterSdks.setSysPath(sdk, listOf("/read/by/updater"))

    assertThat(PythonInterpreterSdks.recordedSysPathForTests(sdk)).containsExactly("/read/by/updater")
  }

  private suspend fun directory(path: Path): VirtualFile {
    Files.createDirectories(path)
    return checkNotNull(edtWriteAction { VfsUtil.createDirectoryIfMissing(path.toString()) }) { "no directory $path" }
  }
}
