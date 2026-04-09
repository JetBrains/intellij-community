// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.pyproject.model

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.writeText
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleTypeId
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.internal.PY_PROJECT_SYSTEM_ID
import com.intellij.python.pyproject.model.internal.autoImportBridge.PyExternalSystemProjectAware
import com.intellij.python.pyproject.model.internal.workspaceBridge.pyProjectTomlEntity
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.utils.vfs.createDirectory
import com.intellij.testFramework.utils.vfs.createFile
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.workspaceModel.ide.legacyBridge.LegacyBridgeJpsEntitySourceFactory
import com.intellij.workspaceModel.ide.toPath
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Path

/** Expected module type marker for pyproject-based modules in [PyProjectTomlSyncTestFixture.assertModuleNames]. */
internal const val PYPROJECT = "PYPROJECT"
/** Standard Python module type ID. */
internal const val PYTHON = "PYTHON_MODULE"
/** Standard Java module type ID. */
internal const val JAVA = "JAVA_MODULE"
/** Name of the bystander Java module created by the fixture to verify it is never modified by sync. */
internal const val BYSTANDER_JAVA = "_bystander_java"
/** Name of the bystander Python module created by the fixture to verify it is never modified by sync. */
internal const val BYSTANDER_PYTHON = "_bystander_python"

/** Creates a `pyproject.toml` with the given `[project].name` inside this directory. */
internal fun VirtualFile.writePyprojectToml(name: String) {
  createFile(PY_PROJECT_TOML).writeText("[project]\nname = \"$name\"")
}

/**
 * Test fixture for pyproject.toml-based module synchronization tests.
 *
 * Creates bystander (non-pyproject) Java and Python modules on init,
 * and verifies they remain untouched on teardown.
 */
internal fun pyProjectTomlSyncFixture(
  projectFixture: TestFixture<Project>,
  tempDirFixture: TestFixture<Path>,
): TestFixture<PyProjectTomlSyncTestFixture> = testFixture {
  val project = projectFixture.init()
  val root = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(tempDirFixture.init())!!
  val fixture = PyProjectTomlSyncTestFixture(project, root)
  fixture.setUp()
  initialized(fixture) {
    fixture.tearDown()
  }
}

/**
 * Shared helper for pyproject.toml module synchronization tests.
 *
 * Provides access to the [project] and its temp [root] directory, along with
 * utilities for creating modules, triggering sync, and asserting workspace model state.
 * Not a base class — test classes obtain an instance via [pyProjectTomlSyncFixture].
 */
internal class PyProjectTomlSyncTestFixture(val project: Project, val root: VirtualFile) {

  lateinit var sut: PyExternalSystemProjectAware

  /** Triggers a full pyproject.toml sync (creates [sut] lazily on first call). */
  suspend fun reloadProject() {
    if (!::sut.isInitialized) sut = PyExternalSystemProjectAware.create(project)
    sut.reloadProjectImpl()
  }

  /** Creates the bystander Java and Python modules that every test expects to remain untouched. */
  suspend fun setUp() {
    addNonPyprojectModule(BYSTANDER_JAVA, BYSTANDER_JAVA, JAVA)
    addNonPyprojectModule(BYSTANDER_PYTHON, BYSTANDER_PYTHON)
  }

  /** Asserts that both bystander modules still exist with their original type, content root, and entity source. */
  fun tearDown() {
    val snapshot = project.workspaceModel.currentSnapshot

    // Verify bystander Java module is untouched
    val javaModule = snapshot.resolve(ModuleId(BYSTANDER_JAVA))
    assertThat(javaModule)
      .describedAs("Bystander Java module '$BYSTANDER_JAVA' must still exist")
      .isNotNull
    assertThat(javaModule!!.type).isEqualTo(ModuleTypeId(JAVA))
    assertThat(javaModule.contentRoots.single().url.toPath().fileName?.toString()).isEqualTo(BYSTANDER_JAVA)
    val javaIsPyProject = (javaModule.entitySource as? JpsImportedEntitySource)?.externalSystemId == PY_PROJECT_SYSTEM_ID.id
    assertThat(javaIsPyProject)
      .describedAs("Bystander Java module must NOT be pyproject-based")
      .isFalse()

    // Verify bystander Python module is untouched
    val pythonModule = snapshot.resolve(ModuleId(BYSTANDER_PYTHON))
    assertThat(pythonModule)
      .describedAs("Bystander Python module '$BYSTANDER_PYTHON' must still exist")
      .isNotNull
    assertThat(pythonModule!!.type).isEqualTo(ModuleTypeId(PYTHON))
    assertThat(pythonModule.contentRoots.single().url.toPath().fileName?.toString()).isEqualTo(BYSTANDER_PYTHON)
    assertThat(pythonModule.pyProjectTomlEntity)
      .describedAs("Bystander Python module must NOT have a pyProjectTomlEntity")
      .isNull()
  }

  /**
   * Adds a non-pyproject module to the workspace model.
   *
   * Creates a directory under [root], registers a [ModuleEntity] with the given [type],
   * and optionally applies additional configuration via [configure].
   */
  suspend fun addNonPyprojectModule(
    name: String,
    dirName: String,
    type: String = PYTHON,
    configure: (ModuleEntityBuilder.() -> Unit)? = null,
  ): VirtualFile {
    val dir = writeAction { root.createDirectory(dirName) }
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
    val dirUrl = dir.toNioPath().toVirtualFileUrl(virtualFileUrlManager)
    val entitySource = LegacyBridgeJpsEntitySourceFactory.getInstance(project)
      .createEntitySourceForModule(dirUrl, null)
    project.workspaceModel.update("add $name module") { storage ->
      storage addEntity ModuleEntity(name, emptyList(), entitySource) {
        this.type = ModuleTypeId(type)
        contentRoots = listOf(ContentRootEntity(dirUrl, emptyList(), entitySource))
        configure?.invoke(this)
      }
    }
    return dir
  }

  /** Returns the file names of all source roots for the given module. */
  fun sourceRootNames(moduleName: String): List<String> {
    val module = project.workspaceModel.currentSnapshot.resolve(ModuleId(moduleName))!!
    return module.contentRoots.flatMap { it.sourceRoots }.map { it.url.toPath().fileName.toString() }
  }

  /** Returns the file names of all excluded URLs for the given module. */
  fun excludeNames(moduleName: String): List<String> {
    val module = project.workspaceModel.currentSnapshot.resolve(ModuleId(moduleName))!!
    return module.contentRoots.flatMap { it.excludedUrls }.map { it.url.toPath().fileName.toString() }
  }

  /** Returns the names of all module dependencies for the given module. */
  fun moduleDeps(moduleName: String): List<String> {
    val module = project.workspaceModel.currentSnapshot.resolve(ModuleId(moduleName))!!
    return module.dependencies.filterIsInstance<ModuleDependency>().map { it.module.name }
  }

  /** Asserts that [parentName]'s source and exclude roots do not overlap with [childName]'s content root. */
  fun assertNoRootsInsideChild(parentName: String, childName: String) {
    val snapshot = project.workspaceModel.currentSnapshot
    val parent = snapshot.resolve(ModuleId(parentName))!!
    val child = snapshot.resolve(ModuleId(childName))!!
    val childContentRoot = child.contentRoots.first().url.toPath()
    for (path in parent.contentRoots.flatMap { it.sourceRoots }.map { it.url.toPath() }) {
      assertThat(path.startsWith(childContentRoot))
        .describedAs("Parent source root '$path' should not be inside child content root '$childContentRoot'")
        .isFalse()
    }
    for (path in parent.contentRoots.flatMap { it.excludedUrls }.map { it.url.toPath() }) {
      assertThat(path.startsWith(childContentRoot))
        .describedAs("Parent exclude '$path' should not be inside child content root '$childContentRoot'")
        .isFalse()
    }
  }

  /**
   * Asserts module names match expectations AND verifies workspace model consistency:
   * every module entity must be resolvable by its symbolic ID (no orphaned/broken references).
   */
  suspend fun assertModuleNames(project: Project, vararg expectedModules: Pair<String, String>) {
    val moduleNames = project.modules.map { it.name }.filter { it != BYSTANDER_JAVA && it != BYSTANDER_PYTHON }.sorted()
    assertThat(moduleNames).containsExactly(*expectedModules.map { it.first }.sorted().toTypedArray())

    // Verify workspace model consistency: every ModuleEntity must be resolvable by its symbolic ID.
    val snapshot = project.workspaceModel.currentSnapshot
    val moduleEntities = snapshot.entities<ModuleEntity>().toList()
    for (entity in moduleEntities) {
      val resolved = snapshot.resolve(ModuleId(entity.name))
      assertThat(resolved)
        .describedAs("ModuleEntity '${entity.name}' must be resolvable by its symbolic ID")
        .isNotNull
      assertThat(entity.contentRoots.toList())
        .describedAs("ModuleEntity '${entity.name}' must have at least one content root")
        .isNotEmpty
    }

    // Verify module type and pyproject entity consistency per expected module.
    val expectedMap = expectedModules.toMap()
    for (entity in moduleEntities) {
      if (entity.name == BYSTANDER_JAVA || entity.name == BYSTANDER_PYTHON) continue
      val isPyProjectBased = (entity.entitySource as? JpsImportedEntitySource)?.externalSystemId == PY_PROJECT_SYSTEM_ID.id
      val expectedType = expectedMap[entity.name]
      assertThat(expectedType)
        .describedAs("Module '${entity.name}' must be in expected list")
        .isNotNull
      when (expectedType) {
        PYPROJECT -> {
          assertThat(entity.type)
            .describedAs("PyProject module '${entity.name}' must have PYTHON_MODULE type")
            .isEqualTo(ModuleTypeId(PYTHON))
          assertThat(isPyProjectBased)
            .describedAs("PyProject module '${entity.name}' must be pyproject-based")
            .isTrue()
          assertThat(entity.pyProjectTomlEntity)
            .describedAs("PyProject module '${entity.name}' must have a PyProjectTomlWorkspaceEntity")
            .isNotNull
          // PY-87499: verify entity source consistency on all children
          for (contentRoot in entity.contentRoots) {
            assertThat((contentRoot.entitySource as? JpsImportedEntitySource)?.externalSystemId)
              .describedAs("ContentRoot of pyproject module '${entity.name}' must have pyproject entity source")
              .isEqualTo(PY_PROJECT_SYSTEM_ID.id)
            for (sourceRoot in contentRoot.sourceRoots) {
              assertThat((sourceRoot.entitySource as? JpsImportedEntitySource)?.externalSystemId)
                .describedAs("SourceRoot '${sourceRoot.url}' in pyproject module '${entity.name}' must have pyproject entity source")
                .isEqualTo(PY_PROJECT_SYSTEM_ID.id)
            }
            for (excludeUrl in contentRoot.excludedUrls) {
              assertThat((excludeUrl.entitySource as? JpsImportedEntitySource)?.externalSystemId)
                .describedAs("ExcludeUrl '${excludeUrl.url}' in pyproject module '${entity.name}' must have pyproject entity source")
                .isEqualTo(PY_PROJECT_SYSTEM_ID.id)
            }
          }
        }
        PYTHON -> {
          assertThat(entity.type)
            .describedAs("Python module '${entity.name}' must have PYTHON_MODULE type")
            .isEqualTo(ModuleTypeId(PYTHON))
          assertThat(isPyProjectBased)
            .describedAs("Python module '${entity.name}' must NOT be pyproject-based")
            .isFalse()
        }
        else -> {
          assertThat(entity.type)
            .describedAs("Module '${entity.name}' must have type '$expectedType'")
            .isEqualTo(ModuleTypeId(expectedType!!))
        }
      }
    }

    // No duplicate symbolic IDs
    val names = moduleEntities.map { it.name }
    assertThat(names).doesNotHaveDuplicates()

    // Every module's content root must be recognized by the file index as a module content root.
    readAction {
      val fileIndex = ProjectFileIndex.getInstance(project)
      for (entity in moduleEntities) {
        for (contentRoot in entity.contentRoots) {
          val dir = contentRoot.url.virtualFile
                    ?: error("Content root '${contentRoot.url}' of module '${entity.name}' must exist on disk")
          val ownerModule = fileIndex.getModuleForFile(dir)
          assertThat(ownerModule)
            .describedAs("Directory '${dir.path}' of module '${entity.name}' must be recognized as a module content root")
            .isNotNull
        }
      }
    }
  }
}
