// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.doNotEnableExternalStorageByDefaultInTestsSuspend
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.registry.Registry
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
import com.intellij.project.stateStore
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.div
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.internal.PY_PROJECT_SYSTEM_ID
import com.intellij.python.pyproject.model.internal.platformBridge.rebuildPyProjectModelForTest
import com.intellij.python.pyproject.model.internal.workspaceBridge.PY_PROJECT_MODEL_OWNS_MODULES_KEY
import com.intellij.python.pyproject.model.internal.workspaceBridge.PyProjectTomlKeptModuleEntitySource
import com.intellij.python.pyproject.model.internal.workspaceBridge.pyProjectTomlEntity
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.TestFixtureInitializer
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.utils.vfs.createDirectory
import com.intellij.testFramework.utils.vfs.createFile
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.workspaceModel.ide.legacyBridge.LegacyBridgeJpsEntitySourceFactory
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.python.sdk.internal.PYTHON_MODULE_ID
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name

/**
 * How the `pyproject.toml` model treats the modules in a test, see [PY_PROJECT_MODEL_OWNS_MODULES_KEY]. A test runs in
 * both: an abstract class holds the tests, and one subclass for each mode sets the key with `@RegistryKeyAppLevel`.
 */
internal enum class ModuleSyncMode {
  /** The model owns its modules, as in PyCharm. */
  OWNING,

  /** The modules belong to the user, as in the other IDEs. */
  KEEPING,
}

/** Expected module type marker for pyproject-based modules in [PyProjectTomlSyncTestFixture.assertProjectStructure]. */
internal const val PYPROJECT = "PYPROJECT"

/** Standard Python module type ID. */
internal const val PYTHON = PYTHON_MODULE_ID

/** Standard Java module type ID. */
internal const val JAVA = "JAVA_MODULE"

/**
 * Describes the expected state of a module after sync.
 *
 * All fields except [name] have sensible defaults for the most common case (pyproject module with no deps).
 */
internal data class ExpectedModule(
  val name: String,
  val type: String = PYPROJECT,
  val contentRoot: String,
  val deps: List<String> = emptyList(),
  val sourceRoots: List<String> = emptyList(),
  val excludedFolders: List<String> = emptyList(),
  val imlFileName: String = "$name.iml",
  /**
   * The name in [ModuleSyncMode.KEEPING], where the model renames no module, or `null` for [name]. The module at the
   * project root keeps the name of the module that the project had, see [PyProjectTomlSyncTestFixture.implicitModuleName].
   */
  val keepingName: String? = null,
)

/**
 * The source roots that [PyProjectTomlSyncTestFixture.assertProjectStructure] expects for [expected].
 *
 * The sync marks `<content root>/src` as a source root of every pyproject module. It does that even when the
 * directory does not exist yet, so a directory the user creates later needs no second sync (PY-89039).
 * A test therefore lists only the extra source roots. `PyProjectTomlImplicitSrcRootTest` states the rule itself.
 */
private fun expectedSourceRoots(expected: ExpectedModule): List<String> {
  if (expected.type != PYPROJECT) return expected.sourceRoots
  val implicitSrc = if (expected.contentRoot == ".") "src" else expected.contentRoot / "src"
  return (expected.sourceRoots + implicitSrc).distinct()
}

/** Name of the bystander Java module created by the fixture to verify it is never modified by sync. */
internal const val BYSTANDER_JAVA = "_bystander_java"

/** Name of the bystander Python module created by the fixture to verify it is never modified by sync. */
internal const val BYSTANDER_PYTHON = "_bystander_python"


/** Creates or modifies a `pyproject.toml` with the given `[project].name` inside this directory. */
@RequiresWriteLock
internal fun VirtualFile.writePyprojectTomlWithProject(projectName: String) {
  writePyprojectToml("[project]\nname = \"$projectName\"")
}

/** Creates a `pyproject.toml` with the given  [content] this directory. */
@RequiresWriteLock
internal fun VirtualFile.writePyprojectToml(content: String) {
  val tomlFile = findChild(PY_PROJECT_TOML) ?: createFile(PY_PROJECT_TOML)
  tomlFile.writeText(content)
}


/**
 * Given [this] is a dir, creates uv workspace out of it. Returns names of children.
 */
@RequiresWriteLock
internal fun VirtualFile.convertDirToUvWorkspace(workspaceName: String = name): List<String> {
  val member1Name = "${workspaceName}_one"
  val member2Name = "${workspaceName}_two"
  createFile(PY_PROJECT_TOML).writeText("""
      [project]
      name = "$workspaceName"
      version = "0.1.0"
      description = "Add your description here"
      readme = "README.md"
      requires-python = ">=3.14"
      dependencies = []

      [tool.uv.workspace]
      members = [
          "$member1Name",
          "$member2Name",
      ]

    """.trimIndent())
  createChildDirectory("", member1Name).writePyprojectTomlWithProject(member1Name)
  createChildDirectory("", member2Name).writePyprojectTomlWithProject(member2Name)
  return listOf(member1Name, member2Name)
}

/**
 * Test fixture for pyproject.toml-based module synchronization tests.
 *
 * The sync root is always the project base path, so the common case needs no arguments at all.
 * The mode comes from [PY_PROJECT_MODEL_OWNS_MODULES_KEY], which each test class sets with `@RegistryKeyAppLevel`.
 *
 * Creates bystander (non-pyproject) Java and Python modules on init,
 * and verifies they remain untouched on teardown.
 */
internal fun pyProjectTomlSyncFixture(projectFixture: TestFixture<Project> = projectFixture()): TestFixture<PyProjectTomlSyncTestFixture> =
  testFixture { syncFixture(projectFixture.init()) }

/**
 * The fixture of [pyProjectTomlSyncFixture] for the class-level [project] that `PyDefaultTestApplication` gives to the
 * constructor of a test class. The test data of `@TestDataPath` is in that project.
 */
internal fun pyProjectTomlSyncFixture(project: Project): TestFixture<PyProjectTomlSyncTestFixture> =
  testFixture { syncFixture(project) }

private suspend fun TestFixtureInitializer.R<PyProjectTomlSyncTestFixture>.syncFixture(
  project: Project,
): TestFixtureInitializer.InitializedTestFixture<PyProjectTomlSyncTestFixture> {
  val mode = if (Registry.`is`(PY_PROJECT_MODEL_OWNS_MODULES_KEY)) ModuleSyncMode.OWNING else ModuleSyncMode.KEEPING
  val root = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(project.stateStore.projectBasePath)!!
  val implicitModuleName = project.modules.singleOrNull()?.name ?: ""
  val fixture = PyProjectTomlSyncTestFixture(project, root, mode, implicitModuleName)
  fixture.setUp()
  return initialized(fixture) {
    fixture.tearDown()
  }
}

/**
 * Shared helper for pyproject.toml module synchronization tests.
 *
 * Provides access to the [project] and its temp [root] directory, along with
 * utilities for creating modules, triggering sync, and asserting workspace model state.
 * Not a base class — test classes obtain an instance via [pyProjectTomlSyncFixture].
 *
 * @param implicitModuleName the name of the implicit Python module created by [PyDefaultTestApplication]
 *        (derived from the test class simple name), used to include it in [assertProjectStructure] expectations.
 */
internal class PyProjectTomlSyncTestFixture(
  val project: Project,
  val root: VirtualFile,
  val mode: ModuleSyncMode,
  val implicitModuleName: String = "",
) {

  /** The name that [expected] has in [mode], see [ExpectedModule.keepingName]. */
  fun nameOf(expected: ExpectedModule): String = when (mode) {
    ModuleSyncMode.OWNING -> expected.name
    ModuleSyncMode.KEEPING -> expected.keepingName
                              ?: implicitModuleName.takeIf { it.isNotEmpty() && expected.type == PYPROJECT && expected.contentRoot == "." }
                              ?: expected.name
  }

  /** Triggers a full pyproject.toml sync. */
  suspend fun reloadProject() {
    // Tests default `Project.isExternalStorageEnabled` to true, which routes pyproject
    // module .iml content to `external_build_system/` instead of `.idea/<name>.iml`.
    // Disable that default so the sync flushes .iml files to the .idea/ folder, matching
    // a freshly-created real project (where external storage is off by default).
    doNotEnableExternalStorageByDefaultInTestsSuspend {
      rebuildPyProjectModelForTest(project)
    }
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
    // Its Python module type can be gone: the migration removes it, see migrateSdkReferences.
    assertThat(pythonModule!!.contentRoots.single().url.toPath().fileName?.toString()).isEqualTo(BYSTANDER_PYTHON)
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
    val dir = edtWriteAction { root.createDirectory(dirName) }
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

  /** Returns paths of all source roots for the given module, relative to the project [root]. */
  fun sourceRootNames(moduleName: String): List<String> {
    val module = project.workspaceModel.currentSnapshot.resolve(ModuleId(moduleName))!!
    val rootPath = root.toNioPath()
    return module.contentRoots.flatMap { it.sourceRoots }.map { rootPath.relativize(it.url.toPath()).toString().ifEmpty { "." } }
  }

  /** Returns paths of all excluded URLs for the given module, relative to the project [root]. */
  fun excludeNames(moduleName: String): List<String> {
    val module = project.workspaceModel.currentSnapshot.resolve(ModuleId(moduleName))!!
    val rootPath = root.toNioPath()
    return module.contentRoots.flatMap { it.excludedUrls }.map { rootPath.relativize(it.url.toPath()).toString().ifEmpty { "." } }
  }

  /**
   * The names of the modules that the `pyproject.toml` of the module depends on, as the model records them in
   * [pyProjectTomlEntity]. Empty for a module without a `pyproject.toml`. The model writes module dependencies only
   * with `PY_PROJECT_MODEL_WRITES_MODULE_DEPENDENCIES_KEY`, see [writtenModuleDeps].
   */
  fun tomlDeps(moduleName: String): List<String> {
    val module = project.workspaceModel.currentSnapshot.resolve(ModuleId(moduleName))!!
    return module.pyProjectTomlEntity?.dependencies.orEmpty()
  }

  /** The names of the modules that the module depends on in the workspace model. */
  fun writtenModuleDeps(moduleName: String): List<String> {
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
   * Asserts the full project structure: module names, types, dependencies, and workspace model consistency.
   * Every module entity must be resolvable by its symbolic ID (no orphaned/broken references).
   */
  suspend fun assertProjectStructure(vararg expectedInAnyMode: ExpectedModule) {
    val expectedModules = expectedInAnyMode.map { expected ->
      val name = nameOf(expected)
      expected.copy(name = name, imlFileName = if (expected.imlFileName == "${expected.name}.iml") "$name.iml" else expected.imlFileName)
    }
    val actualNames = project.modules.map { it.name }.filter { it != BYSTANDER_JAVA && it != BYSTANDER_PYTHON }.sorted()
    assertThat(actualNames).containsExactly(*expectedModules.map { it.name }.sorted().toTypedArray())

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

    // Verify the pyproject entity consistency per expected module. The model writes no module type, so a Python module has
    // whatever type the IDE gave it.
    val expectedMap = expectedModules.associateBy { it.name }
    for (entity in moduleEntities) {
      if (entity.name == BYSTANDER_JAVA || entity.name == BYSTANDER_PYTHON) continue
      val isPyProjectBased = (entity.entitySource as? JpsImportedEntitySource)?.externalSystemId == PY_PROJECT_SYSTEM_ID.id
      val expected = expectedMap[entity.name]
      assertThat(expected)
        .describedAs("Module '${entity.name}' must be in expected list")
        .isNotNull
      when (expected!!.type) {
        PYPROJECT -> {
          assertThat(entity.pyProjectTomlEntity)
            .describedAs("PyProject module '${entity.name}' must have a PyProjectTomlWorkspaceEntity")
            .isNotNull
          if (mode == ModuleSyncMode.KEEPING) {
            assertThat(isPyProjectBased)
              .describedAs("The model keeps the entity source of module '${entity.name}'")
              .isFalse()
            assertThat(entity.pyProjectTomlEntity?.entitySource)
              .describedAs("The PyProjectTomlWorkspaceEntity of module '${entity.name}' is in memory only")
              .isEqualTo(PyProjectTomlKeptModuleEntitySource)
            continue
          }
          assertThat(isPyProjectBased)
            .describedAs("PyProject module '${entity.name}' must be pyproject-based")
            .isTrue()
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
          assertThat(isPyProjectBased)
            .describedAs("Python module '${entity.name}' must NOT be pyproject-based")
            .isFalse()
        }
        else -> {
          assertThat(entity.type)
            .describedAs("Module '${entity.name}' must have type '${expected.type}'")
            .isEqualTo(ModuleTypeId(expected.type))
        }
      }
    }

    // Verify the dependencies that the model records for each pyproject.toml.
    for (expected in expectedModules) {
      val actualDeps = tomlDeps(expected.name)
      assertThat(actualDeps)
        .describedAs("Module '${expected.name}' dependencies")
        .containsExactlyInAnyOrderElementsOf(expected.deps)
    }

    // Verify content root, source roots, and excluded folders.
    val rootPath = root.toNioPath()
    for (expected in expectedModules) {
      val entity = snapshot.resolve(ModuleId(expected.name))!!
      val relativePath = rootPath.relativize(entity.contentRoots.single().url.toPath()).toString().ifEmpty { "." }
      assertThat(relativePath)
        .describedAs("Module '${expected.name}' content root")
        .isEqualTo(expected.contentRoot)
      assertThat(sourceRootNames(expected.name))
        .describedAs("Module '${expected.name}' source roots")
        .containsExactlyInAnyOrderElementsOf(expectedSourceRoots(expected))
      assertThat(excludeNames(expected.name))
        .describedAs("Module '${expected.name}' excluded folders")
        .containsExactlyInAnyOrderElementsOf(expected.excludedFolders)
    }

    // No duplicate symbolic IDs
    val names = moduleEntities.map { it.name }
    assertThat(names).doesNotHaveDuplicates()

    // Verify .iml filenames on disk for each expected module.
    // The pyproject bridge flushes .iml files to disk via saveSettings() so they should be visible to VCS.
    val moduleManager = ModuleManager.getInstance(project)
    for (expected in expectedModules) {
      val module = moduleManager.findModuleByName(expected.name)
                   ?: error("Module '${expected.name}' not found by ModuleManager")
      val imlPath = Path.of(module.moduleFilePath)
      assertThat(imlPath.name)
        .describedAs("Module '${expected.name}' .iml filename")
        .isEqualTo(expected.imlFileName)
      assertThat(imlPath.exists())
        .describedAs("Module '${expected.name}' .iml file must be flushed to disk at $imlPath")
        .isTrue()
    }

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
