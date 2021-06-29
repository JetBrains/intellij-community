// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.ProjectModelRule
import com.jetbrains.python.PythonMockSdk
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.psi.PyUtil
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class PySdkPathsTest {

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Test
  fun sysPathEntryIsExcludedPath() {
    val sdk = PythonMockSdk.create()

    val excluded = createInSdkRoot(sdk, "my_excluded")
    val included = createInSdkRoot(sdk, "my_included")

    sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, listOf(sdk.homePath, excluded.path, included.path))
    runWriteActionAndWait { sdk.getOrCreateAdditionalData() }.apply { setExcludedPathsFromVirtualFiles(setOf(excluded)) }

    PythonSdkUpdater.updateVersionAndPathsSynchronouslyAndScheduleRemaining(sdk, projectModel.project)
    ApplicationManager.getApplication().invokeAndWait { PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() }

    val rootProvider = sdk.rootProvider
    val sdkRoots = rootProvider.getFiles(OrderRootType.CLASSES)
    assertThat(sdkRoots).contains(included)
    assertThat(sdkRoots).doesNotContain(excluded)
    assertThat(rootProvider.getFiles(OrderRootType.SOURCES)).isEmpty()

    Disposer.dispose(PythonPluginDisposable.getInstance()) // dispose virtual file pointer containers in sdk additional data
  }

  @Test
  fun sysPathEntryIsModuleRoot() {
    val moduleRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(
      FileUtil.createTempDirectory("my", "project", false)
    )!!.also { deleteOnTearDown(it) }

    val module = projectModel.createModule()
    assertThat(PyUtil.getSourceRoots(module)).isEmpty()

    module.rootManager.modifiableModel.apply {
      addContentEntry(moduleRoot)
      runWriteActionAndWait { commit() }
    }
    assertThat(PyUtil.getSourceRoots(module)).containsOnly(moduleRoot)

    val sdk = PythonMockSdk.create()
    sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, listOf(sdk.homePath, moduleRoot.path))

    PythonSdkUpdater.updateVersionAndPathsSynchronouslyAndScheduleRemaining(sdk, projectModel.project)
    ApplicationManager.getApplication().invokeAndWait { PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() }

    assertThat(PyUtil.getSourceRoots(module)).containsOnly(moduleRoot)

    val rootProvider = sdk.rootProvider
    assertThat(rootProvider.getFiles(OrderRootType.CLASSES)).doesNotContain(moduleRoot)
    assertThat(rootProvider.getFiles(OrderRootType.SOURCES)).isEmpty()
  }

  @Test
  fun sysPathEntryInModuleAndSdkInModuleButEntryNotInSdk() {
    val moduleRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(
      FileUtil.createTempDirectory("my", "project", false)
    )!!.also { deleteOnTearDown(it) }

    val sdkPath = runWriteActionAndWait {
      val venv = moduleRoot.createChildDirectory(this, "venv")

      venv.createChildData(this, "pyvenv.cfg")  // see PythonSdkUtil.getVirtualEnvRoot

      val bin = venv.createChildDirectory(this, "bin")
      bin.createChildData(this, "python")

      return@runWriteActionAndWait venv.path
    }

    val entryPath = runWriteActionAndWait { moduleRoot.createChildDirectory(this, "mylib") }

    val module = projectModel.createModule()
    assertThat(PyUtil.getSourceRoots(module)).isEmpty()

    module.rootManager.modifiableModel.apply {
      addContentEntry(moduleRoot)
      runWriteActionAndWait { commit() }
    }
    assertThat(PyUtil.getSourceRoots(module)).containsOnly(moduleRoot)

    val sdk = PythonMockSdk.create(sdkPath)
    sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, listOf(sdk.homePath, entryPath.path))

    PythonSdkUpdater.updateVersionAndPathsSynchronouslyAndScheduleRemaining(sdk, projectModel.project)
    ApplicationManager.getApplication().invokeAndWait { PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() }

    assertThat(PyUtil.getSourceRoots(module)).containsExactlyInAnyOrder(moduleRoot, entryPath)

    val rootProvider = sdk.rootProvider
    assertThat(rootProvider.getFiles(OrderRootType.CLASSES)).doesNotContain(entryPath)
    assertThat(rootProvider.getFiles(OrderRootType.SOURCES)).isEmpty()
  }

  @Test
  fun sysPathEntryInSdkAndSdkInModule() {
    val moduleRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(
      FileUtil.createTempDirectory("my", "project", false)
    )!!.also { deleteOnTearDown(it) }

    val sdkDir = runWriteActionAndWait {
      val venv = moduleRoot.createChildDirectory(this, "venv")

      venv.createChildData(this, "pyvenv.cfg")  // see PythonSdkUtil.getVirtualEnvRoot

      val bin = venv.createChildDirectory(this, "bin")
      bin.createChildData(this, "python")

      return@runWriteActionAndWait venv
    }

    val entryPath = runWriteActionAndWait { sdkDir.createChildDirectory(this, "mylib") }

    val module = projectModel.createModule()
    assertThat(PyUtil.getSourceRoots(module)).isEmpty()

    module.rootManager.modifiableModel.apply {
      addContentEntry(moduleRoot)
      runWriteActionAndWait { commit() }
    }
    assertThat(PyUtil.getSourceRoots(module)).containsOnly(moduleRoot)

    val sdk = PythonMockSdk.create(sdkDir.path)
    sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, listOf(sdk.homePath, entryPath.path))

    PythonSdkUpdater.updateVersionAndPathsSynchronouslyAndScheduleRemaining(sdk, projectModel.project)
    ApplicationManager.getApplication().invokeAndWait { PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() }

    assertThat(PyUtil.getSourceRoots(module)).containsOnly(moduleRoot)

    val rootProvider = sdk.rootProvider
    assertThat(rootProvider.getFiles(OrderRootType.CLASSES)).contains(entryPath)
    assertThat(rootProvider.getFiles(OrderRootType.SOURCES)).isEmpty()
  }

  private fun createInSdkRoot(sdk: Sdk, relativePath: String): VirtualFile {
    return runWriteActionAndWait {
      VfsUtil.createDirectoryIfMissing(sdk.homeDirectory!!.parent.parent, relativePath)
    }.also { deleteOnTearDown(it) }
  }

  private fun deleteOnTearDown(file: VirtualFile) {
    Disposer.register(projectModel.project) { VfsTestUtil.deleteFile(file) }
  }
}