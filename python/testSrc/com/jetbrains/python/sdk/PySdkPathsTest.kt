// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
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
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.rules.ProjectModelRule
import com.jetbrains.python.PythonMockSdk
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.psi.PyUtil
import org.jetbrains.annotations.NotNull
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
    mockPythonPluginDisposable()
    runWriteActionAndWait { sdk.getOrCreateAdditionalData() }.apply { setExcludedPathsFromVirtualFiles(setOf(excluded)) }

    updateSdkPaths(sdk)

    Disposer.dispose(PythonPluginDisposable.getInstance()) // dispose virtual file pointer containers in sdk additional data

    val rootProvider = sdk.rootProvider
    val sdkRoots = rootProvider.getFiles(OrderRootType.CLASSES)
    assertThat(sdkRoots).contains(included)
    assertThat(sdkRoots).doesNotContain(excluded)
    assertThat(rootProvider.getFiles(OrderRootType.SOURCES)).isEmpty()
  }

  @Test
  fun userAddedIsModuleRoot() {
    val (module, moduleRoot) = createModule()

    val sdk = PythonMockSdk.create()
    mockPythonPluginDisposable()
    runWriteActionAndWait { sdk.getOrCreateAdditionalData() }.apply { setAddedPathsFromVirtualFiles(setOf(moduleRoot)) }

    updateSdkPaths(sdk)

    Disposer.dispose(PythonPluginDisposable.getInstance()) // dispose virtual file pointer containers in sdk additional data

    checkRoots(sdk, module, listOf(moduleRoot), emptyList())
  }

  @Test
  fun sysPathEntryIsModuleRoot() {
    val (module, moduleRoot) = createModule()

    val sdk = PythonMockSdk.create()
    sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, listOf(sdk.homePath, moduleRoot.path))

    updateSdkPaths(sdk)

    checkRoots(sdk, module, listOf(moduleRoot), emptyList())
  }

  @Test
  fun userAddedInModuleAndSdkInModuleButUserAddedNotInSdk() {
    val (module, moduleRoot) = createModule()

    val sdkPath = createVenvStructureInModule(moduleRoot).path

    val userAddedPath = createSubdir(moduleRoot)

    val sdk = PythonMockSdk.create(sdkPath)
    mockPythonPluginDisposable()
    runWriteActionAndWait { sdk.getOrCreateAdditionalData() }.apply { setAddedPathsFromVirtualFiles(setOf(userAddedPath)) }

    updateSdkPaths(sdk)

    Disposer.dispose(PythonPluginDisposable.getInstance()) // dispose virtual file pointer containers in sdk additional data

    checkRoots(sdk, module, listOf(moduleRoot, userAddedPath), emptyList())
  }

  @Test
  fun sysPathEntryInModuleAndSdkInModuleButEntryNotInSdk() {
    val (module, moduleRoot) = createModule()

    val sdkPath = createVenvStructureInModule(moduleRoot).path

    val entryPath = createSubdir(moduleRoot)

    val sdk = PythonMockSdk.create(sdkPath)
    sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, listOf(sdk.homePath, entryPath.path))

    updateSdkPaths(sdk)

    checkRoots(sdk, module, listOf(moduleRoot, entryPath), emptyList())
  }

  @Test
  fun userAddedInSdkAndSdkInModule() {
    val (module, moduleRoot) = createModule()

    val sdkDir = createVenvStructureInModule(moduleRoot)

    val userAddedPath = createSubdir(sdkDir)

    val sdk = PythonMockSdk.create(sdkDir.path)
    mockPythonPluginDisposable()
    runWriteActionAndWait { sdk.getOrCreateAdditionalData() }.apply { setAddedPathsFromVirtualFiles(setOf(userAddedPath)) }

    updateSdkPaths(sdk)

    Disposer.dispose(PythonPluginDisposable.getInstance()) // dispose virtual file pointer containers in sdk additional data

    checkRoots(sdk, module, listOf(moduleRoot), listOf(userAddedPath))
  }

  @Test
  fun sysPathEntryInSdkAndSdkInModule() {
    val (module, moduleRoot) = createModule()

    val sdkDir = createVenvStructureInModule(moduleRoot)

    val entryPath = createSubdir(sdkDir)

    val sdk = PythonMockSdk.create(sdkDir.path)
    sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, listOf(sdk.homePath, entryPath.path))

    updateSdkPaths(sdk)

    checkRoots(sdk, module, listOf(moduleRoot), listOf(entryPath))
  }

  private fun createModule(): Pair<Module, VirtualFile> {
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

    return module to moduleRoot
  }

  private fun createVenvStructureInModule(moduleRoot: VirtualFile): VirtualFile {
    return runWriteActionAndWait {
      val venv = moduleRoot.createChildDirectory(this, "venv")

      venv.createChildData(this, "pyvenv.cfg")  // see PythonSdkUtil.getVirtualEnvRoot

      val bin = venv.createChildDirectory(this, "bin")
      bin.createChildData(this, "python")

      venv
    }
  }

  private fun createSubdir(dir: VirtualFile): VirtualFile {
    return runWriteActionAndWait { dir.createChildDirectory(this, "mylib") }
  }

  private fun mockPythonPluginDisposable() {
    ApplicationManager.getApplication().replaceService(PythonPluginDisposable::class.java, PythonPluginDisposable(), projectModel.project)
  }

  private fun updateSdkPaths(sdk: @NotNull Sdk) {
    PythonSdkUpdater.updateVersionAndPathsSynchronouslyAndScheduleRemaining(sdk, projectModel.project)
    ApplicationManager.getApplication().invokeAndWait { PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() }
  }

  private fun checkRoots(sdk: Sdk, module: Module, moduleRoots: List<VirtualFile>, sdkRoots: List<VirtualFile>) {
    assertThat(PyUtil.getSourceRoots(module)).containsExactlyInAnyOrder(*moduleRoots.toTypedArray())

    val rootProvider = sdk.rootProvider
    val classes = rootProvider.getFiles(OrderRootType.CLASSES)
    assertThat(classes).containsAll(sdkRoots)
    assertThat(classes).doesNotContain(*moduleRoots.toTypedArray())
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