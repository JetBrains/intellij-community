// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model

import com.intellij.facet.FacetType
import com.intellij.facet.mock.MockFacetType
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.writeText
import com.intellij.platform.ModuleAttachProcessor
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.FacetEntityTypeId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.platform.workspace.jps.entities.modifyContentRootEntity
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.jps.entities.sdkId
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.SEP
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.api.ModelRebuiltListener
import com.intellij.python.pyproject.model.internal.MODEL_REBUILD
import com.intellij.python.pyproject.model.internal.autoImportBridge.createWsmTracker
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.vfs.createDirectory
import com.intellij.testFramework.utils.vfs.createFile
import com.intellij.util.io.createDirectories
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for pyproject.toml module lifecycle: exclude/un-exclude behavior, module adoption,
 * facet preservation, and dependency updates.
 */
@TestApplication
internal class PyProjectTomlLifecycleTest {

  companion object {
    private val log = fileLogger()
    private val disposableFixture = disposableFixture()

    @JvmStatic
    @BeforeAll
    fun beforeAll() {
      @Suppress("MISSING_DEPENDENCY_SUPERCLASS_IN_TYPE_ARGUMENT")
      FacetType.EP_NAME.point.registerExtension(MockFacetType(), disposableFixture.get())
    }
  }

  private val tempDirFixture = tempPathFixture()
  private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)


  @Test
  fun `attach workspace`(@TempDir workspace2Parent: Path): Unit = timeoutRunBlocking {

    val projectRebuiltTimesCounter = AtomicInteger()
    val projectRebuiltTwoTimes = CompletableDeferred<Unit>()
    projectFixture.get().messageBus.connect(projectFixture.get())
      .subscribe(MODEL_REBUILD,
                 ModelRebuiltListener {
                   if (projectRebuiltTimesCounter.incrementAndGet() == 2) { // <-- Wait for 2 rebuilds: initial and after new proj attached
                     projectRebuiltTwoTimes.complete(Unit)
                   }
                 })

    val workspace1Members = edtWriteAction {
      f.root.convertDirToUvWorkspace("workspace1")
    }
    f.reloadProject() // <-- Reload 1
    val (workspace2, workspace2Members) = edtWriteAction {
      val workspace2 =
        VirtualFileManager.getInstance().refreshAndFindFileByNioPath(workspace2Parent.resolve("workspace2").createDirectories())!!
      val workspace2Members = workspace2.convertDirToUvWorkspace()
      Pair(workspace2, workspace2Members)
    }
    val scope = projectFixture.get().service<MyService>().scope

    // To be unlocked when WSM updated with new module
    val wsmTrackedDef = CompletableDeferred<Unit>()
    val tracker = scope.createWsmTracker(projectFixture.get()) {
      scope.launch {
        // Project rebuild doesn't work in tests, so we listen for WSM and update it explicitly
        f.reloadProject() // <-- Reload 2
        wsmTrackedDef.complete(Unit)
      }
    }
    try {
      val moduleAttachedDef = CompletableDeferred<Unit>()
      val workspace2Nio = workspace2.toNioPath()
      ModuleAttachProcessor().attachToProjectAsync(f.project, workspace2Nio, { _, _ -> moduleAttachedDef.complete(Unit) })
      log.info("Waiting for the module attach callback")
      moduleAttachedDef.await()
      log.info("Waiting for the WSM update")
      wsmTrackedDef.await()
      log.info("Waiting for project rebuild")
      withTimeout(7.seconds) {
        projectRebuiltTwoTimes.await()
      }
      val root = f.root.toNioPath()

      val expectedStruct = buildList {
        add(ExpectedModule("workspace1", contentRoot = "."))
        for (member in workspace1Members) {
          add(ExpectedModule(member, contentRoot = member))
        }
        add(ExpectedModule("workspace2", contentRoot = root.relativize(workspace2Nio).pathString))
        for (member in workspace2Members) {
          add(ExpectedModule(member, contentRoot = root.relativize(workspace2Nio.resolve(member)).pathString))
        }
      }.toTypedArray()

      f.assertProjectStructure(*expectedStruct)
    }
    finally {
      tracker.cancelAndJoin()
    }
  }

  /**
   * pyproject.toml inside an excluded folder should not create a module.
   * When the folder is un-excluded, the module should appear on the next reload.
   */
  @Test
  fun `excluded folder pyproject is ignored and restored on un-exclude`(): Unit = timeoutRunBlocking(30.seconds) {
    edtWriteAction {
      f.root.writePyprojectToml("root")
      f.root.createDirectory("sub").writePyprojectToml("child")
    }

    f.reloadProject()
    f.assertProjectStructure(ExpectedModule("child", contentRoot = "sub"), ExpectedModule("root", contentRoot = "."))

    // Exclude the sub directory on the root module's content root
    val virtualFileUrlManager = f.project.workspaceModel.getVirtualFileUrlManager()
    val subUrl = f.root.findChild("sub")!!.toNioPath().toVirtualFileUrl(virtualFileUrlManager)
    f.project.workspaceModel.update("exclude sub") { storage ->
      val rootModule = storage.entities<ModuleEntity>().first { it.name == "root" }
      val contentRoot = rootModule.contentRoots.first()
      storage.modifyContentRootEntity(contentRoot) {
        excludedUrls = excludedUrls + ExcludeUrlEntity(subUrl, this.entitySource)
      }
    }

    // Reload — child module should be removed (pyproject.toml is inside excluded folder)
    f.reloadProject()
    f.assertProjectStructure(ExpectedModule("root", contentRoot = ".", excludedFolders = listOf("sub")))

    // Un-exclude by removing the ExcludeUrlEntity
    f.project.workspaceModel.update("un-exclude sub") { storage ->
      val rootModule = storage.entities<ModuleEntity>().first { it.name == "root" }
      val contentRoot = rootModule.contentRoots.first()
      storage.modifyContentRootEntity(contentRoot) {
        excludedUrls = excludedUrls.filterNot { it.url == subUrl }
      }
    }

    // Reload — child module should reappear
    f.reloadProject()
    // Use basic name check instead of assertModuleNames — the file index may not immediately
    // recognize a content root that was just un-excluded and re-created.
    val moduleNames = f.project.modules.map { it.name }
      .filter { it != BYSTANDER_JAVA && it != BYSTANDER_PYTHON }.sorted()
    assertThat(moduleNames).containsExactly("child", "root")
  }

  /**
   * When a non-pyproject Python module exists and a pyproject.toml is added at the same location
   * with the same name, the module should be adopted and become pyproject-based.
   * The module name stays "lib" throughout — no suffix, no rename. Pre-existing exclude and
   * source roots must survive adoption with consistent pyproject entity sources (PY-87499).
   */
  @Test
  fun `non-pyproject Python module adopted by pyproject sync`(): Unit = timeoutRunBlocking(30.seconds) {
    val libDir = f.addNonPyprojectModule("lib", "lib")

    // Add exclude and source roots to the pre-existing module before adoption
    val virtualFileUrlManager = f.project.workspaceModel.getVirtualFileUrlManager()
    val nodeModulesDir = edtWriteAction { libDir.createDirectory("node_modules") }
    val srcDir = edtWriteAction { libDir.createDirectory("src") }
    f.project.workspaceModel.update("add exclude and source root") { storage ->
      val libModule = storage.resolve(ModuleId("lib"))!!
      val contentRoot = libModule.contentRoots.first()
      storage.modifyContentRootEntity(contentRoot) {
        excludedUrls = excludedUrls + ExcludeUrlEntity(
          nodeModulesDir.toNioPath().toVirtualFileUrl(virtualFileUrlManager), this.entitySource)
        sourceRoots = sourceRoots + SourceRootEntity(
          srcDir.toNioPath().toVirtualFileUrl(virtualFileUrlManager), SourceRootTypeId("java-source"), this.entitySource)
      }
    }

    edtWriteAction {
      f.root.writePyprojectToml("root")
      libDir.writePyprojectToml("lib")
    }

    f.reloadProject()
    // assertModuleNames verifies entity source consistency on all children (PY-87499)
    f.assertProjectStructure(
      ExpectedModule("lib", contentRoot = "lib", sourceRoots = listOf("lib${SEP}src"), excludedFolders = listOf("lib${SEP}node_modules")),
      ExpectedModule("root", contentRoot = "."),
    )
  }

  /**
   * Verifies that facets are preserved when a non-pyproject Python module is adopted by pyproject sync
   * and when an existing pyproject module is renamed.
   */
  @Test
  fun `facets preserved on module adoption and rename`(): Unit = timeoutRunBlocking(30.seconds) {
    val libDir = f.addNonPyprojectModule("lib", "lib") {
      facets = listOf(FacetEntity(ModuleId("lib"), "TestFacet", FacetEntityTypeId("MockFacetId"), entitySource))
    }

    // Create pyproject.toml at same location (adopts "lib") + another module "app"
    edtWriteAction {
      f.root.writePyprojectToml("root")
      libDir.writePyprojectToml("lib")
      f.root.createDirectory("app").writePyprojectToml("old-app")
    }

    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("lib", contentRoot = "lib"),
      ExpectedModule("old-app", contentRoot = "app"),
      ExpectedModule("root", contentRoot = "."),
    )

    // Verify facet survived adoption
    val snapshot1 = f.project.workspaceModel.currentSnapshot
    val libFacets1 = snapshot1.resolve(ModuleId("lib"))!!.facets
    assertThat(libFacets1).describedAs("Facet must survive module adoption").hasSize(1)
    assertThat(libFacets1.first().name).isEqualTo("TestFacet")

    // Add a facet to "old-app" to test rename preservation
    f.project.workspaceModel.update("add facet to old-app") { storage ->
      val appModule = storage.resolve(ModuleId("old-app"))!!
      storage.modifyModuleEntity(appModule) {
        facets = listOf(FacetEntity(ModuleId("old-app"), "AppFacet", FacetEntityTypeId("MockFacetId"), appModule.entitySource))
      }
    }

    // Rename "old-app" to "new-app" via pyproject.toml
    edtWriteAction {
      f.root.findChild("app")!!.findChild(PY_PROJECT_TOML)!!.writeText("[project]\nname = \"new-app\"")
    }

    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("lib", contentRoot = "lib"),
      ExpectedModule("new-app", contentRoot = "app"),
      ExpectedModule("root", contentRoot = "."),
    )

    // Verify facets survived rename
    val snapshot2 = f.project.workspaceModel.currentSnapshot
    val libFacets2 = snapshot2.resolve(ModuleId("lib"))!!.facets
    assertThat(libFacets2).describedAs("Facet must survive after second sync").hasSize(1)
    assertThat(libFacets2.first().name).isEqualTo("TestFacet")

    val appFacets = snapshot2.resolve(ModuleId("new-app"))!!.facets
    assertThat(appFacets).describedAs("Facet must survive module rename").hasSize(1)
    assertThat(appFacets.first().name).isEqualTo("AppFacet")
  }

  /**
   * Verifies that inter-module dependencies are updated when pyproject.toml dependency declarations change.
   * Initial sync: A depends on B. After editing both pyproject.toml files to reverse the dependency (B->A),
   * a re-sync should reflect the new dependency direction. Also verifies that a user-configured SDK
   * is preserved across the dependency update.
   */
  @Test
  fun `dependencies updated when pyproject dependency direction reverses`(): Unit = timeoutRunBlocking(30.seconds) {
    val aDir = edtWriteAction { f.root.createDirectory("a") }
    val bDir = edtWriteAction { f.root.createDirectory("b") }
    val bUri = bDir.toNioPath().toUri()
    val aUri = aDir.toNioPath().toUri()

    // Initial sync: A depends on B
    edtWriteAction {
      f.root.writePyprojectToml("root")
      aDir.createFile(PY_PROJECT_TOML).writeText("[project]\nname = \"A\"\ndependencies = [\"B @ $bUri\"]")
      bDir.writePyprojectToml("B")
    }

    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("A", contentRoot = "a", deps = listOf("B")),
      ExpectedModule("B", contentRoot = "b"),
      ExpectedModule("root", contentRoot = "."),
    )

    // Simulate user configuring an SDK on module A
    val fakeSdkId = SdkId("Python 3.12", "Python SDK")
    f.project.workspaceModel.update("set SDK") { storage ->
      val moduleA = storage.resolve(ModuleId("A"))!!
      storage.modifyModuleEntity(moduleA) {
        sdkId = fakeSdkId
      }
    }

    // Reverse: B depends on A, A has no deps
    edtWriteAction {
      aDir.findChild(PY_PROJECT_TOML)!!.writeText("[project]\nname = \"A\"")
      bDir.findChild(PY_PROJECT_TOML)!!.writeText("[project]\nname = \"B\"\ndependencies = [\"A @ $aUri\"]")
    }

    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("A", contentRoot = "a"),
      ExpectedModule("B", contentRoot = "b", deps = listOf("A")),
      ExpectedModule("root", contentRoot = "."),
    )

    // Verify SDK is preserved on module A
    val moduleA2 = f.project.workspaceModel.currentSnapshot.resolve(ModuleId("A"))!!
    assertThat(moduleA2.sdkId).describedAs("SDK should be preserved across dependency update")
      .isEqualTo(fakeSdkId)
  }

  /**
   * When a module is removed during sync, its source roots and excluded folders
   * that still physically exist on disk must be relocated to the parent module.
   */
  @Test
  fun `source roots and excluded folders relocated to parent on module deletion`(): Unit = timeoutRunBlocking(30.seconds) {
    edtWriteAction {
      f.root.writePyprojectToml("root")
      f.root.createDirectory("sub").writePyprojectToml("child")
    }

    f.reloadProject()
    f.assertProjectStructure(ExpectedModule("child", contentRoot = "sub"), ExpectedModule("root", contentRoot = "."))

    // Create physical directories for a source root and an excluded folder inside the child module
    val virtualFileUrlManager = f.project.workspaceModel.getVirtualFileUrlManager()
    val subUrl = f.root.findChild("sub")!!.toNioPath().toVirtualFileUrl(virtualFileUrlManager)
    val srcDir = edtWriteAction { f.root.findChild("sub")!!.createDirectory("src") }
    val cacheDir = edtWriteAction { f.root.findChild("sub")!!.createDirectory("__pycache__") }

    f.project.workspaceModel.update("add source root, exclude, and self-exclude on child module") { storage ->
      val childModule = storage.entities<ModuleEntity>().first { it.name == "child" }
      val contentRoot = childModule.contentRoots.first()
      storage.modifyContentRootEntity(contentRoot) {
        sourceRoots = sourceRoots + SourceRootEntity(
          srcDir.toNioPath().toVirtualFileUrl(virtualFileUrlManager), SourceRootTypeId("java-source"), this.entitySource)
        excludedUrls = excludedUrls + listOf(
          ExcludeUrlEntity(cacheDir.toNioPath().toVirtualFileUrl(virtualFileUrlManager), this.entitySource),
          ExcludeUrlEntity(subUrl, this.entitySource),
        )
      }
    }

    // Reload — the excluded content root makes the FS walker skip sub/, so child becomes an orphan
    f.reloadProject()

    // The child module should be gone, and root should have inherited:
    // - source root: sub/src
    // - excluded folders: sub and sub/__pycache__
    f.assertProjectStructure(ExpectedModule(
      "root", contentRoot = ".",
      sourceRoots = listOf("sub${SEP}src"),
      excludedFolders = listOf("sub", "sub${SEP}__pycache__"),
    ))
  }

  /**
   * Source roots and excluded folders pointing to directories that no longer exist on disk
   * must NOT be relocated to the parent module when the child module is deleted.
   */
  @Test
  fun `non-existing source roots and excludes are not relocated on module deletion`(): Unit = timeoutRunBlocking(30.seconds) {
    edtWriteAction {
      f.root.writePyprojectToml("root")
      f.root.createDirectory("sub").writePyprojectToml("child")
    }

    f.reloadProject()
    f.assertProjectStructure(ExpectedModule("child", contentRoot = "sub"), ExpectedModule("root", contentRoot = "."))

    val virtualFileUrlManager = f.project.workspaceModel.getVirtualFileUrlManager()
    val subUrl = f.root.findChild("sub")!!.toNioPath().toVirtualFileUrl(virtualFileUrlManager)
    // Create directories, capture their URLs, then delete them
    val vanishedSrc = edtWriteAction { f.root.findChild("sub")!!.createDirectory("vanished_src") }
    val vanishedCache = edtWriteAction { f.root.findChild("sub")!!.createDirectory("vanished_cache") }
    val vanishedSrcUrl = vanishedSrc.toNioPath().toVirtualFileUrl(virtualFileUrlManager)
    val vanishedCacheUrl = vanishedCache.toNioPath().toVirtualFileUrl(virtualFileUrlManager)
    edtWriteAction {
      vanishedSrc.delete(this)
      vanishedCache.delete(this)
    }

    f.project.workspaceModel.update("add stale roots and self-exclude on child module") { storage ->
      val childModule = storage.entities<ModuleEntity>().first { it.name == "child" }
      val contentRoot = childModule.contentRoots.first()
      storage.modifyContentRootEntity(contentRoot) {
        sourceRoots = sourceRoots + SourceRootEntity(vanishedSrcUrl, SourceRootTypeId("java-source"), this.entitySource)
        excludedUrls = excludedUrls + listOf(
          ExcludeUrlEntity(vanishedCacheUrl, this.entitySource),
          ExcludeUrlEntity(subUrl, this.entitySource),
        )
      }
    }

    f.reloadProject()

    // Only "sub" exclusion should be relocated (it exists); vanished roots should be dropped
    f.assertProjectStructure(ExpectedModule("root", contentRoot = ".", excludedFolders = listOf("sub")))
  }
}

@Service(Service.Level.PROJECT)
private class MyService(val scope: CoroutineScope)
