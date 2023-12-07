// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
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
import com.intellij.workspaceModel.ide.legacyBridge.GlobalSdkTableBridge
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonMockSdk
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyUtil
import org.jetbrains.annotations.NotNull
import org.junit.*

class PySdkPathsTest {

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Before
  fun setUp() {
    Assume.assumeTrue("Test has to be executed on a new implementation of SDK", GlobalSdkTableBridge.isEnabled())
  }

  @Test
  fun sysPathEntryIsExcludedPath() {
    val sdk = PythonMockSdk.create()

    val excluded = createInSdkRoot(sdk, "my_excluded")
    val included = createInSdkRoot(sdk, "my_included")

    sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, listOf(sdk.homePath, excluded.path, included.path))
    mockPythonPluginDisposable()
    runWriteActionAndWait {
      sdk.getOrCreateAdditionalData()

      sdk.sdkModificator.apply {
        (sdkAdditionalData as PythonSdkAdditionalData).setExcludedPathsFromVirtualFiles(setOf(excluded))
        commitChanges()
      }
    }

    updateSdkPaths(sdk)

    val rootProvider = sdk.rootProvider
    val sdkRoots = rootProvider.getFiles(OrderRootType.CLASSES)
    assertThat(sdkRoots).contains(included)
    assertThat(sdkRoots).doesNotContain(excluded)
    assertThat(rootProvider.getFiles(OrderRootType.SOURCES)).isEmpty()
  }

  @Test
  fun userAddedIsModuleRoot() {
    val (module, moduleRoot) = createModule()

    val sdk = PythonMockSdk.create().also {
      registerSdk(it)
      module.pythonSdk = it
    }
    mockPythonPluginDisposable()
    runWriteActionAndWait { sdk.getOrCreateAdditionalData() }.apply { setAddedPathsFromVirtualFiles(setOf(moduleRoot)) }

    updateSdkPaths(sdk)

    checkRoots(sdk, module, listOf(moduleRoot), emptyList())
    assertThat(getPathsToTransfer(sdk)).doesNotContain(moduleRoot)
  }

  @Test
  fun sysPathEntryIsModuleRoot() {
    val (module, moduleRoot) = createModule()

    val sdk = PythonMockSdk.create().also {
      registerSdk(it)
      module.pythonSdk = it
    }
    sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, listOf(sdk.homePath, moduleRoot.path))

    mockPythonPluginDisposable()
    updateSdkPaths(sdk)

    checkRoots(sdk, module, listOf(moduleRoot), emptyList())
    assertThat(getPathsToTransfer(sdk)).doesNotContain(moduleRoot)
  }

  @Test
  fun userAddedInModuleAndSdkInModuleButUserAddedNotInSdk() {
    val (module, moduleRoot) = createModule()

    val sdkPath = createVenvStructureInModule(moduleRoot).path

    val userAddedPath = createSubdir(moduleRoot)

    val pythonVersion = LanguageLevel.getLatest().toPythonVersion()
    val sdk = PythonMockSdk.create(sdkPath)
    registerSdk(sdk)
    sdk.putUserData(PythonSdkType.MOCK_PY_VERSION_KEY, pythonVersion)
    module.pythonSdk = sdk

    mockPythonPluginDisposable()
    runWriteActionAndWait {
      sdk.getOrCreateAdditionalData()

      sdk.sdkModificator.apply {
        (sdkAdditionalData as PythonSdkAdditionalData).setAddedPathsFromVirtualFiles(setOf(userAddedPath))
        commitChanges()
      }
    }

    updateSdkPaths(sdk)

    checkRoots(sdk, module, listOf(moduleRoot, userAddedPath), emptyList())

    val simpleSdk = PythonMockSdk.create().also {
      removeTransferredRoots(module, sdk)
      module.pythonSdk = it
    }

    updateSdkPaths(simpleSdk)

    checkRoots(simpleSdk, module, listOf(moduleRoot), emptyList())
  }

  @Test
  fun userAddedViaEditableSdkWithSharedData() {
    // emulates com.jetbrains.python.configuration.PythonSdkDetailsDialog.ShowPathButton.actionPerformed

    val (module, moduleRoot) = createModule()

    val sdkPath = createVenvStructureInModule(moduleRoot).path

    val userAddedPath = createSubdir(moduleRoot)

    mockPythonPluginDisposable()

    val pythonVersion = LanguageLevel.getDefault().toPythonVersion()
    val sdk = ProjectJdkTable.getInstance().createSdk("Mock ${PyNames.PYTHON_SDK_ID_NAME} $pythonVersion", PythonSdkType.getInstance())
    sdk.sdkModificator.apply {
      homePath = "$sdkPath/bin/python"
      versionString = pythonVersion
      runWriteActionAndWait {
        commitChanges()
        sdk.getOrCreateAdditionalData()
      }
    }
    registerSdk(sdk)
    module.pythonSdk = sdk
    sdk.putUserData(PythonSdkType.MOCK_PY_VERSION_KEY, pythonVersion)

    val projectSdksModel = PyConfigurableInterpreterList.getInstance(projectModel.project).model
    val editableSdk = projectSdksModel.findSdk(sdk.name)
    editableSdk!!.putUserData(PythonSdkType.MOCK_PY_VERSION_KEY, pythonVersion)

    // --- ADD path ---
    editableSdk.sdkModificator.apply {
      (sdkAdditionalData as PythonSdkAdditionalData).setAddedPathsFromVirtualFiles(setOf(userAddedPath))
      runWriteActionAndWait {
        commitChanges()
        projectSdksModel.apply()
      }
    }

    updateSdkPaths(editableSdk)
    updateSdkPaths(sdk)

    checkRoots(sdk, module, listOf(moduleRoot, userAddedPath), emptyList())

    // --- REMOVE path ---
    editableSdk.sdkModificator.apply {
      (sdkAdditionalData as PythonSdkAdditionalData).setAddedPathsFromVirtualFiles(emptySet())
      runWriteActionAndWait {
        commitChanges()
        projectSdksModel.apply()
      }
    }

    updateSdkPaths(editableSdk)
    updateSdkPaths(sdk)

    checkRoots(sdk, module, listOf(moduleRoot), emptyList())

    runWriteActionAndWait { ProjectJdkTable.getInstance().removeJdk(sdk) }
  }

  @Test
  fun userAddedViaEditableSdkWithoutSharedData() {
    // emulates com.jetbrains.python.configuration.PythonSdkDetailsDialog.ShowPathButton.actionPerformed

    val (module, moduleRoot) = createModule()

    val sdkPath = createVenvStructureInModule(moduleRoot).path

    val userAddedPath = createSubdir(moduleRoot)

    val pythonVersion = LanguageLevel.getDefault().toPythonVersion()

    val sdk = ProjectJdkTable.getInstance().createSdk("Mock ${PyNames.PYTHON_SDK_ID_NAME} $pythonVersion", PythonSdkType.getInstance())
    sdk.sdkModificator.apply {
      versionString = pythonVersion
      homePath = "$sdkPath/bin/python"
      runWriteActionAndWait { commitChanges() }
    }

    registerSdk(sdk)
    module.pythonSdk = sdk
    sdk.putUserData(PythonSdkType.MOCK_PY_VERSION_KEY, pythonVersion)

    val projectSdksModel = PyConfigurableInterpreterList.getInstance(projectModel.project).model
    val editableSdk = projectSdksModel.findSdk(sdk.name)
    editableSdk!!.putUserData(PythonSdkType.MOCK_PY_VERSION_KEY, pythonVersion)

    // --- ADD path ---
    editableSdk.sdkModificator.apply {
      assertThat(sdkAdditionalData).isNull()
      mockPythonPluginDisposable()
      sdkAdditionalData = PythonSdkAdditionalData().apply {
        setAddedPathsFromVirtualFiles(setOf(userAddedPath))
      }
      runWriteActionAndWait {
        commitChanges()
        ProjectJdkTable.getInstance().updateJdk(sdk, editableSdk)
      }
    }

    updateSdkPaths(editableSdk)
    updateSdkPaths(sdk)

    checkRoots(sdk, module, listOf(moduleRoot, userAddedPath), emptyList())

    // --- REMOVE path ---
    editableSdk.sdkModificator.apply {
      (sdkAdditionalData as PythonSdkAdditionalData).setAddedPathsFromVirtualFiles(emptySet())
      runWriteActionAndWait {
        commitChanges()
        projectSdksModel.apply()
      }
    }

    updateSdkPaths(editableSdk)

    updateSdkPaths(sdk) // after updateJdk call editableSdk and sdk share the same data

    checkRoots(sdk, module, listOf(moduleRoot), emptyList())

    runWriteActionAndWait { ProjectJdkTable.getInstance().removeJdk(sdk) }
  }

  @Test
  fun sysPathEntryInModuleAndSdkInModuleButEntryNotInSdk() {
    val (module, moduleRoot) = createModule()

    val sdkPath = createVenvStructureInModule(moduleRoot).path

    val entryPath = createSubdir(moduleRoot)

    val sdk = PythonMockSdk.create(sdkPath).also {
      registerSdk(it)
      module.pythonSdk = it
    }
    sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, listOf(sdk.homePath, entryPath.path))

    mockPythonPluginDisposable()
    updateSdkPaths(sdk)
    checkRoots(sdk, module, listOf(moduleRoot, entryPath), emptyList())

    // Subsequent updates should keep already set up source roots
    updateSdkPaths(sdk)
    checkRoots(sdk, module, listOf(moduleRoot, entryPath), emptyList())

    val simpleSdk = PythonMockSdk.create().also {
      removeTransferredRoots(module, sdk)
      module.pythonSdk = it
    }

    updateSdkPaths(simpleSdk)

    checkRoots(simpleSdk, module, listOf(moduleRoot), emptyList())
  }

  @Test
  fun userAddedInSdkAndSdkInModule() {
    val (module, moduleRoot) = createModule()

    val sdkDir = createVenvStructureInModule(moduleRoot)

    val userAddedPath = createSubdir(sdkDir)

    val pythonVersion = LanguageLevel.getLatest().toPythonVersion()
    val sdk = PythonMockSdk.create(sdkDir.path).also {
      registerSdk(it)
      module.pythonSdk = it
    }
    sdk.putUserData(PythonSdkType.MOCK_PY_VERSION_KEY, pythonVersion)
    mockPythonPluginDisposable()

    runWriteActionAndWait {
      sdk.getOrCreateAdditionalData()

      sdk.sdkModificator.apply {
        (sdkAdditionalData as PythonSdkAdditionalData).setAddedPathsFromVirtualFiles(setOf(userAddedPath))
        commitChanges()
      }
    }

    updateSdkPaths(sdk)

    checkRoots(sdk, module, listOf(moduleRoot), listOf(userAddedPath))
  }

  @Test
  fun sysPathEntryInSdkAndSdkInModule() {
    val (module, moduleRoot) = createModule()

    val sdkDir = createVenvStructureInModule(moduleRoot)

    val entryPath = createSubdir(sdkDir)

    val sdk = PythonMockSdk.create(sdkDir.path).also {
      registerSdk(it)
      module.pythonSdk = it
    }
    sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, listOf(sdk.homePath, entryPath.path))

    updateSdkPaths(sdk)

    checkRoots(sdk, module, listOf(moduleRoot), listOf(entryPath))
  }

  @Test
  fun sysPathEntryOutsideSdkAndModule1ButInsideModule2() {
    val (module1, moduleRoot1) = createModule("m1")
    val (module2, moduleRoot2) = createModule("m2")

    val sdkDir = createVenvStructureInModule(moduleRoot1)

    val entryPath1 = createSubdir(moduleRoot1)
    val entryPath2 = createSubdir(moduleRoot2)

    val sdk = PythonMockSdk.create().let {
      val properSdk = PythonMockSdk.create("Mock SDK without path", sdkDir.path, it.sdkType, LanguageLevel.getLatest())
      registerSdk(properSdk)
      module1.pythonSdk = properSdk
      module2.pythonSdk = properSdk
      return@let properSdk
    }
    sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, listOf(sdk.homePath, entryPath1.path, entryPath2.path))

    mockPythonPluginDisposable()
    updateSdkPaths(sdk)

    checkRoots(sdk, module1, listOf(moduleRoot1, entryPath1, entryPath2), emptyList())
    checkRoots(sdk, module2, listOf(moduleRoot2, entryPath1, entryPath2), emptyList())

    val simpleSdk = PythonMockSdk.create().also {
      registerSdk(it)
      removeTransferredRoots(module1, sdk)
      module1.pythonSdk = it

      removeTransferredRoots(module2, sdk)
      module2.pythonSdk = it
    }

    updateSdkPaths(simpleSdk)

    checkRoots(simpleSdk, module1, listOf(moduleRoot1), emptyList())
    checkRoots(simpleSdk, module2, listOf(moduleRoot2), emptyList())
  }

  private fun registerSdk(it: Sdk) {
    WriteAction.runAndWait<RuntimeException> {
      ProjectJdkTable.getInstance().addJdk(it, projectModel.disposableRule.disposable)
    }
  }

  private fun createModule(name: String = "module"): Pair<Module, VirtualFile> {
    val moduleRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(
      FileUtil.createTempDirectory("my", "project", false)
    )!!.also { deleteOnTearDown(it) }

    val module = projectModel.createModule(name)
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
    Disposer.register(projectModel.project, PythonPluginDisposable.getInstance())
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