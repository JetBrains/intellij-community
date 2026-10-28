// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.jetbrains.python.sdk.internal.PYTHON_MODULE_ID
import com.jetbrains.python.fixtures.awaitPythonInterpreters
import com.jetbrains.python.fixtures.syncPythonInterpreterLibraries
import com.intellij.python.pyproject.model.evolution.interpreterSourceRootUrls
import com.intellij.openapi.project.Project
import com.jetbrains.python.sdk.setPythonSdkForTests
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.venv.sdk.flavors.VirtualEnvSdkFlavor
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.RegistryKeyRule
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.rules.ProjectModelRule
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import org.jdom.Element
import org.jetbrains.annotations.NotNull
import org.junit.Assume.assumeTrue
import org.junit.ClassRule
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test

@Subsystems.Interpreters
@Layers.Functional
internal class PySdkPathsTest {

  companion object {
    /**
     * The test sets the interpreter of the module as the interpreter library flow does: it adds no SDK to the table, and
     * it checks the source roots that the flow derives. The key is defined once the application loads the plugins, so it
     * is set after [ApplicationRule].
     */
    @JvmField
    @ClassRule
    val appRule: RuleChain = RuleChain.outerRule(ApplicationRule()).around(RegistryKeyRule("python.interpreter.library.entries", true))
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Test
  fun sysPathEntryIsExcludedPath() {
    createModule()
    val sdk = PythonMockSdk.create()

    val excluded = createInSdkRoot(sdk, "my_excluded")
    val included = createInSdkRoot(sdk, "my_included")

    sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, listOf(sdk.homePath, excluded.path, included.path))
    mockPythonPluginDisposable()
    runWriteActionAndWait {
      sdk.pySdkAdditionalData

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

    mockPythonPluginDisposable()

    val sdk = PythonMockSdk.create().also {
      // The owner is part of the interpreter's key, so it is set before the module gets the interpreter.
      it.associateWith(moduleRoot)
      module.setPythonSdkForTests(it)
    }

    // Associated like its sibling `sysPathEntryIsModuleRoot`, because `updateSdkPaths` classifies a path against the
    // roots of the module the SDK names, and a mock SDK names none.
    // Committed through the modificator, so the next commit of this SDK keeps the path. Written in place it was
    // dropped by whichever commit came first, and this test then passed because nothing was left to classify.
    runWriteActionAndWait {
      sdk.sdkModificator.apply {
        (sdkAdditionalData as PythonSdkAdditionalData).setAddedPathsFromVirtualFiles(setOf(moduleRoot))
        commitChanges()
      }
    }

    updateSdkPaths(sdk)

    checkRoots(sdk, module, listOf(moduleRoot), emptyList())
    assertThat(getPathsToTransfer(sdk)).doesNotContain(moduleRoot)
  }

  @Test
  fun sysPathEntryIsModuleRoot() {
    val (module, moduleRoot) = createModule()

    val sdk = PythonMockSdk.create().also {
      // The owner is part of the interpreter's key, so it is set before the module gets the interpreter.
      it.associateWith(moduleRoot)
      module.setPythonSdkForTests(it)
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
    sdk.putUserData(PythonSdkType.MOCK_PY_VERSION_KEY, pythonVersion)
    // The owner is part of the interpreter's key, so it is set before the module gets the interpreter.
    sdk.associateWith(moduleRoot)
    module.setPythonSdkForTests(sdk)

    mockPythonPluginDisposable()
    runWriteActionAndWait {
      sdk.pySdkAdditionalData

      sdk.sdkModificator.apply {
        (sdkAdditionalData as PythonSdkAdditionalData).setAddedPathsFromVirtualFiles(setOf(userAddedPath))
        commitChanges()
      }
    }

    updateSdkPaths(sdk)

    checkRoots(sdk, module, listOf(moduleRoot, userAddedPath), emptyList())

    val simpleSdk = PythonMockSdk.create().also {
      removeTransferredRoots(module, sdk)
      module.setPythonSdkForTests(it)
    }

    updateSdkPaths(simpleSdk)

    checkRoots(simpleSdk, module, listOf(moduleRoot), emptyList())
  }

  @Test
  fun sysPathEntryInModuleAndSdkInModuleButEntryNotInSdk() {
    val (module, moduleRoot) = createModule()

    val sdkPath = createVenvStructureInModule(moduleRoot).path

    val entryPath = createSubdir(moduleRoot)

    val sdk = PythonMockSdk.create(sdkPath).also {
      // The owner is part of the interpreter's key, so it is set before the module gets the interpreter.
      it.associateWith(moduleRoot)
      module.setPythonSdkForTests(it)
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
      module.setPythonSdkForTests(it)
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
    val sdk = venvMockSdk(sdkDir).also {
      module.setPythonSdkForTests(it)
    }
    sdk.putUserData(PythonSdkType.MOCK_PY_VERSION_KEY, pythonVersion)
    mockPythonPluginDisposable()

    runWriteActionAndWait {
      sdk.pySdkAdditionalData

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

    val sdk = venvMockSdk(sdkDir).also {
      module.setPythonSdkForTests(it)
    }
    sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, listOf(sdk.homePath, entryPath.path))

    updateSdkPaths(sdk)

    checkRoots(sdk, module, listOf(moduleRoot), listOf(entryPath))
  }

  @Test
  fun sysPathEntryInsideAnotherModuleDoesNotConfigureSourceRootThere() {
    val (module1, moduleRoot1) = createModule("m1")
    val (_, moduleRoot2) = createModule("m2")

    val entryPath1 = createSubdir(moduleRoot1)
    val entryPath2 = createSubdir(moduleRoot2)

    val sdk = venvMockSdk(createVenvStructureInModule(moduleRoot1)).also {
      // The owner is part of the interpreter's key, so it is set before the module gets the interpreter.
      it.associateWith(moduleRoot1)
      module1.setPythonSdkForTests(it)
    }
    sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, listOf(sdk.homePath, entryPath1.path, entryPath2.path))

    mockPythonPluginDisposable()
    updateSdkPaths(sdk)

    checkRoots(sdk, module1, listOf(moduleRoot1, entryPath1), emptyList())
  }

  /**
   * An editable package with its own `pyproject.toml` is a module nested in the project. Its directory is on `sys.path`,
   * twice when a `.pth` file and its finder both add it, and the outer module gets no source root for it.
   */
  @Test
  fun sysPathEntryThatIsANestedModuleRootConfiguresNoSourceRoot() {
    val (outer, outerRoot) = createModule("outer")
    val nestedRoot = runWriteActionAndWait { outerRoot.createChildDirectory(this, "editable") }
    val nested = runWriteActionAndWait {
      projectModel.moduleManager.newModule(projectModel.projectRootDir.resolve("nested/nested.iml"), PYTHON_MODULE_ID)
    }
    ModuleRootManager.getInstance(nested).modifiableModel.apply {
      addContentEntry(nestedRoot)
      runWriteActionAndWait { commit() }
    }
    val entryPath = createSubdir(outerRoot)

    val sdk = venvMockSdk(createVenvStructureInModule(outerRoot)).also {
      it.associateWith(outerRoot)
      outer.setPythonSdkForTests(it)
    }
    sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, listOf(sdk.homePath, nestedRoot.path, nestedRoot.path, entryPath.path))

    mockPythonPluginDisposable()
    updateSdkPaths(sdk)

    checkRoots(sdk, outer, listOf(outerRoot, entryPath), emptyList())
  }

  /** A path of sys.path that the user excludes in the interpreter paths is no source root of the module any more. */
  @Test
  fun excludedSysPathEntryIsNoSourceRoot() {
    val (module, moduleRoot) = createModule()
    val entryPath = createSubdir(moduleRoot)
    val sdk = PythonMockSdk.create(createVenvStructureInModule(moduleRoot).path).also {
      it.associateWith(moduleRoot)
      module.setPythonSdkForTests(it)
    }
    sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, listOf(sdk.homePath, entryPath.path))
    mockPythonPluginDisposable()
    updateSdkPaths(sdk)
    checkRoots(sdk, module, listOf(moduleRoot, entryPath), emptyList())

    runWriteActionAndWait {
      sdk.sdkModificator.apply {
        (sdkAdditionalData as PythonSdkAdditionalData).setExcludedPathsFromVirtualFiles(setOf(entryPath))
        commitChanges()
      }
    }
    updateSdkPaths(sdk)

    checkRoots(sdk, module, listOf(moduleRoot), emptyList())
  }

  /** The source roots of an interpreter look to the Project Structure page as a root of the module file does: no change. */
  @Test
  fun interpreterSourceRootsLeaveTheRootModelUnchanged() {
    val (module, moduleRoot) = createModule()
    val entryPath = createSubdir(moduleRoot)
    val sdk = PythonMockSdk.create(createVenvStructureInModule(moduleRoot).path).also {
      it.associateWith(moduleRoot)
      module.setPythonSdkForTests(it)
    }
    sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, listOf(sdk.homePath, entryPath.path))

    mockPythonPluginDisposable()
    updateSdkPaths(sdk)
    checkRoots(sdk, module, listOf(moduleRoot, entryPath), emptyList())
    // The page tells these roots apart from the roots of the module file, and does not let the user change them.
    assertThat(interpreterSourceRootUrls(module)).containsExactly(entryPath.url)

    val model = runReadActionBlocking { ModuleRootManager.getInstance(module).modifiableModel }
    try {
      // The page reads the properties of each source root, which builds them for a root that has none.
      model.contentEntries.flatMap { it.sourceFolders.toList() }.forEach { it.jpsElement.properties }
      assertThat(model.isChanged).isFalse()
    }
    finally {
      runWriteActionAndWait { model.dispose() }
    }
  }

  @Test
  fun sysPathEntryPointingToAnotherModuleRootConfiguresModuleDependency() {
    assumeTrue("The registry key 'python.detect.cross.module.dependencies' is not enabled",
               Registry.`is`("python.detect.cross.module.dependencies"))

    val (module1, moduleRoot1) = createModule("m1")
    val (module2, moduleRoot2) = createModule("m2")

    val sdk = venvMockSdk(createVenvStructureInModule(moduleRoot1)).also {
      module1.setPythonSdkForTests(it)
    }
    mockPythonPluginDisposable()

    sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, listOf(moduleRoot2.path))
    updateSdkPaths(sdk)
    checkRoots(sdk, module1,
               moduleRoots = listOf(moduleRoot1),
               sdkRoots = listOf(),
               moduleDependencies = listOf(module2))

    sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, listOf())
    updateSdkPaths(sdk)
    checkRoots(sdk, module1,
               moduleRoots = listOf(moduleRoot1),
               sdkRoots = listOf(),
               moduleDependencies = listOf())
  }

  /**
   * PY-88807: SDKs with remote home paths (Docker Compose, SFTP, etc.) that lost their
   * additional data during upgrades must not crash [pySdkAdditionalData].
   * Simulates the real scenario: an SDK is serialized with a remote home path, then
   * deserialized — [PythonSdkType.loadAdditionalData] returns [PyInvalidSdk] for stale
   * remote interpreters, and [pySdkAdditionalData] must recognize it.
   */
  @Test
  fun getOrCreateAdditionalDataForRemoteSdkDoesNotCrash() {
    mockPythonPluginDisposable()
    val sdkType = PythonSdkType.getInstance()

    for (remotePath in listOf(
      "docker-compose://[/home/user/project/docker-compose.yml]:gossip//usr/local/bin/python",
      "sftp://root@127.0.0.1:2222/virtualenv/bin/python",
      "docker://python:latest/usr/local/bin/python",
    )) {
      val sdk = ProjectJdkTable.getInstance().createSdk("Remote SDK", sdkType) as ProjectJdkImpl
      runWriteActionAndWait {
        sdk.sdkModificator.apply {
          // Simulate an old SDK that had remote additional data from a previous IDE version that doesn't exist anymore
          // and one of non-local home paths
          homePath = remotePath
          sdkAdditionalData = PythonSdkAdditionalData(
            PyFlavorAndData(PyFlavorData.Empty, VirtualEnvSdkFlavor.getInstance()),
            projectModel.projectRootDir
          )
          commitChanges()
        }
      }
      // Round-trip: serialize then deserialize — readExternal triggers loadAdditionalData
      val element = Element("jdk")
      sdk.writeExternal(element)
      sdk.readExternal(element)

      assertThat(sdk.sdkAdditionalData).isInstanceOf(PyInvalidSdk::class.java)
      assertThat(sdk.pySdkAdditionalData).isInstanceOf(PyInvalidSdk::class.java)
    }
  }

  /**
   * PY-93451: an SDK on an eel has a UNC home path. [PythonSdkType.loadAdditionalData] must keep its data
   * when `python.eel.native` is off, because the IDE saves [PyInvalidSdk] as empty data.
   */
  @Test
  fun eelSdkIsNotInvalidWhenEelNativeModeIsOff() {
    mockPythonPluginDisposable()
    Registry.get("python.eel.native").setValue(false, projectModel.project)
    val sdkType = PythonSdkType.getInstance()

    for (eelPath in listOf(
      "\\\\wsl$\\Ubuntu\\home\\user\\venv\\bin\\python",
      "//wsl.localhost/Ubuntu/home/user/venv/bin/python",
    )) {
      val sdk = ProjectJdkTable.getInstance().createSdk("WSL SDK", sdkType) as ProjectJdkImpl
      runWriteActionAndWait {
        sdk.sdkModificator.apply {
          homePath = eelPath
          sdkAdditionalData = PythonSdkAdditionalData(
            PyFlavorAndData(PyFlavorData.Empty, VirtualEnvSdkFlavor.getInstance()),
            projectModel.projectRootDir
          )
          commitChanges()
        }
      }
      val element = Element("jdk")
      sdk.writeExternal(element)
      sdk.readExternal(element)

      assertThat(sdk.sdkAdditionalData).describedAs(eelPath).isNotInstanceOf(PyInvalidSdk::class.java)
      assertThat((sdk.sdkAdditionalData as PythonSdkAdditionalData).flavor).describedAs(eelPath).isEqualTo(VirtualEnvSdkFlavor.getInstance())
    }
  }

  /**
   * PY-90400: the SDK's own binary skeletons (and bundled typeshed) live under `~/.cache`. When the
   * project's content root is an ancestor of that cache — e.g. in remote dev, where the whole home
   * directory is opened as the project — those SDK-owned roots sit under a module content root but
   * outside the interpreter's venv. The PY-86494 "project-local path" safety net must not drop them.
   */
  @Test
  fun skeletonsUnderContentRootAreKept() {
    mockPythonPluginDisposable()

    val sdk = PythonMockSdk.create()

    // The SDK's binary skeletons live at <systemPath>/python_stubs/<hash>. Open the enclosing
    // python_stubs directory as the module's content root so the skeletons dir sits under it, while
    // the interpreter home (MockSdk test data) stays outside it — reproducing the reported topology.
    val skeletonsRoot = runWriteActionAndWait {
      VfsUtil.createDirectoryIfMissing(PythonSdkUtil.getSkeletonsRootPath(PathManager.getSystemPath()))
    }!!

    val module = projectModel.createModule("home")
    ModuleRootManager.getInstance(module).modifiableModel.apply {
      addContentEntry(skeletonsRoot)
      runWriteActionAndWait { commit() }
    }
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)
    module.setPythonSdkForTests(sdk)

    updateSdkPaths(sdk)

    val skeletonsDir = StandardFileSystems.local().refreshAndFindFileByPath(PythonSdkUtil.getSkeletonsPath(sdk)!!)
    assertThat(skeletonsDir).describedAs("SDK skeletons directory should exist after the update").isNotNull
    assertThat(sdk.rootProvider.getFiles(OrderRootType.CLASSES))
      .describedAs("SDK-owned skeletons root under a content root must not be dropped (PY-90400)")
      .contains(skeletonsDir)
  }

  /**
   * PY-86494: with the update triggered by a `null` project (the synchronous creation/headless path), the updater
   * must classify the owner's project-local entry via the SDK's own `associatedModulePath` — not the triggering
   * project — and, since the owning project is open, transfer it as a source root instead of committing it as an
   * SDK CLASSES (library) root (which moved the library root off `.venv` onto the project folder). Mirrors
   * [sysPathEntryInModuleAndSdkInModuleButEntryNotInSdk], but triggered with no project.
   */
  @Test
  fun projectLocalSysPathEntryIsResolvedViaAssociationWithoutTriggeringProject() {
    val (module, moduleRoot) = createModule()

    val sdkPath = createVenvStructureInModule(moduleRoot).path
    val entryPath = createSubdir(moduleRoot)

    val sdk = PythonMockSdk.create(sdkPath).also {
      // The owner is part of the interpreter's key, so it is set before the module gets the interpreter.
      it.associateWith(moduleRoot)
      module.setPythonSdkForTests(it)
    }
    sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, listOf(sdk.homePath, entryPath.path))

    mockPythonPluginDisposable()

    updateSdkPaths(sdk, project = null)

    checkRoots(sdk, module, moduleRoots = listOf(moduleRoot, entryPath), sdkRoots = emptyList())
  }


  /** Associates the SDK with a module directory, as venv/uv SDK creation does; the updater keys project-local classification off it. */
  /**
   * A mock SDK of the venv in [venvDir], see [createVenvStructureInModule]. Its home is the binary the venv has, which is
   * `Scripts/python.exe` on Windows. A home that does not exist names no interpreter.
   */
  private fun venvMockSdk(venvDir: VirtualFile): Sdk {
    val sdk = PythonMockSdk.create(venvDir.path)
    val binary = if (localEel.platform.isWindows) "Scripts/python.exe" else "bin/python"
    runWriteActionAndWait {
      sdk.sdkModificator.apply {
        homePath = venvDir.toNioPath().resolve(binary).toString()
        commitChanges()
      }
    }
    return sdk
  }

  private fun Sdk.associateWith(moduleDir: VirtualFile) {
    runWriteActionAndWait {
      sdkModificator.apply {
        (sdkAdditionalData as PythonSdkAdditionalData).associatedModulePath = moduleDir.path
        commitChanges()
      }
    }
  }

  private fun createModule(name: String = "module"): Pair<Module, VirtualFile> {
    val moduleRoot = StandardFileSystems.local().refreshAndFindFileByPath(
      FileUtil.createTempDirectory("my", "project", false).absolutePath
    )!!.also { deleteOnTearDown(it) }

    // A Python module, so the snapshot holds a Python project for it: the updater transfers roots only to a module whose
    // Python project uses the SDK.
    val module = runWriteActionAndWait {
      projectModel.moduleManager.newModule(projectModel.projectRootDir.resolve("$name/$name.iml"), PYTHON_MODULE_ID)
    }
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)
    assertThat(PyUtil.getSourceRoots(module)).isEmpty()

    ModuleRootManager.getInstance(module).modifiableModel.apply {
      addContentEntry(moduleRoot)
      runWriteActionAndWait { commit() }
    }
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)
    assertThat(PyUtil.getSourceRoots(module)).containsOnly(moduleRoot)

    return module to moduleRoot
  }

  private fun createVenvStructureInModule(moduleRoot: VirtualFile): VirtualFile {
    return runWriteActionAndWait {
      val venv = moduleRoot.createChildDirectory(this, "venv")

      venv.createChildData(this, "pyvenv.cfg")  // the marker the venv environment provider looks for

      // The layout of a real venv: the venv detection does not accept `bin` on Windows.
      // PythonEnvironment.detectPythonEnvironment requires an executable binary.
      val (binDir, binary) = if (localEel.platform.isWindows) "Scripts" to "python.exe" else "bin" to "python"
      val bin = venv.createChildDirectory(this, binDir)
      bin.createChildData(this, binary).toNioPath().toFile().setExecutable(true)

      venv.createChildDirectory(this, "lib")

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

  /**
   * Updates the paths of [sdk], see [PythonSdkUpdater], with [project] as the project that asks. In the interpreter
   * library flow a module gets the source roots of its interpreter from the library sync, so the sync runs next.
   */
  private fun updateSdkPaths(sdk: @NotNull Sdk, project: Project? = projectModel.project) {
    awaitPythonInterpreters(projectModel.project)
    PythonSdkUpdater.updateVersionAndPathsSynchronouslyAndScheduleRemaining(sdk, project)
    ApplicationManager.getApplication().invokeAndWait { PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() }
    syncPythonInterpreterLibraries(projectModel.project)
  }

  private fun checkRoots(
    sdk: Sdk,
    module: Module,
    moduleRoots: List<VirtualFile>,
    sdkRoots: List<VirtualFile>,
    moduleDependencies: List<Module> = emptyList(),
  ) {
    assertThat(PyUtil.getSourceRoots(module)).containsExactlyInAnyOrder(*moduleRoots.toTypedArray())

    val rootProvider = sdk.rootProvider
    val classes = rootProvider.getFiles(OrderRootType.CLASSES)
    assertThat(classes).containsAll(sdkRoots)
    assertThat(classes).doesNotContain(*moduleRoots.toTypedArray())
    assertThat(rootProvider.getFiles(OrderRootType.SOURCES)).isEmpty()

    val actualModuleDependencies = ModuleRootManager.getInstance(module).getModifiableModel().moduleDependencies
    assertThat(actualModuleDependencies).isEqualTo(moduleDependencies.toTypedArray())
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