// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.evolution

import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.java.workspace.entities.javaSourceRoots
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.storeAndGet
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.LibraryTypeId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.platform.workspace.jps.entities.modifyContentRootEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.python.sdk.backend.PY_INTERPRETER_LIBRARY_KIND_ID
import com.intellij.python.sdk.backend.PyInterpreterLibraryName
import com.intellij.python.sdk.backend.PythonInterpreterDetails
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.python.sdk.backend.classRoots
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.isInterpreterLibraryFlowEnabled
import com.intellij.python.sdk.backend.pyInterpreterDependency
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * The source of the interpreter libraries, see [PyInterpreterLibraryName], and of the source roots that a module gets
 * from the `sys.path` of its interpreter, see [PythonInterpreterDetails.sourceRoots]. It is no file, so the workspace
 * model never saves them: no `.iml` stores these roots, they hold local paths, and live only in the cache of this
 * machine. [PyInterpreterLibrarySync] builds them again from the interpreters.
 */
internal object PyInterpreterLibraryEntitySource : EntitySource

/**
 * Builds the interpreter library of each Python project, and the source roots that its module gets from the `sys.path`
 * of the interpreter, see [PythonInterpreterDetails.sourceRoots].
 *
 * A module stores only the name of its interpreter library. The library itself holds the roots of the interpreter, and
 * is not stored, see [PyInterpreterLibraryEntitySource]. So this service builds it again. [EvoPyProjectModel] calls
 * [sync] in each pass, before it publishes the snapshot, so the snapshot never names a library without roots. The
 * service also calls it when the roots of an interpreter change, which no snapshot reports.
 *
 * The libraries of the project are replaced together, so a library that no module names any more goes away.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class PyInterpreterLibrarySync(private val project: Project, private val scope: CoroutineScope) {
  internal fun start() {
    scope.launch {
      // The SDK updater writes the new roots of an interpreter, which no snapshot reports.
      PythonInterpreterDetails.getInstance(project).changes
        .conflate()
        .collect { sync(EvoPyProjectModel.getInstance(project).snapshotOrNull()?.evoPyProjects ?: return@collect) }
    }
  }

  /**
   * Makes the interpreter libraries of the project hold the roots of the interpreters of [evoPyProjects], and gives each
   * module the source roots of its interpreter.
   */
  internal suspend fun sync(evoPyProjects: Sequence<EvoPyProject>) {
    // The legacy flow reads the SDK of a module and has no interpreter libraries. Leave the libraries alone, so a
    // return to the library flow finds them.
    if (!isInterpreterLibraryFlowEnabled()) return
    syncLibraries(evoPyProjects)
    syncSourceRoots(evoPyProjects)
  }

  private suspend fun syncLibraries(evoPyProjects: Sequence<EvoPyProject>) {
    val workspaceModel = project.workspaceModel
    val urlManager = workspaceModel.getVirtualFileUrlManager()
    val storage = workspaceModel.currentSnapshot
    val wanted = mutableMapOf<String, List<VirtualFile>>()
    for (evoPyProject in evoPyProjects) {
      val interpreter = evoPyProject.interpreter ?: continue
      val name = evoPyProject.pyProject.residesOnModule.findModuleEntity(storage)?.dependencies?.pyInterpreterDependency?.library?.name ?: continue
      wanted[name] = interpreter.classRoots(project).toList()
    }

    val current = storage.entities<LibraryEntity>()
      .filter { it.entitySource == PyInterpreterLibraryEntitySource }
      .associate { it.name to it.roots.map { root -> root.url.url } }
    if (current == wanted.mapValues { (_, roots) -> roots.map { it.url } }) return

    workspaceModel.update("Build the Python interpreter libraries") { storage ->
      // Only the libraries: the source roots of the same source stay, see [syncSourceRoots].
      storage.entities<LibraryEntity>().filter { it.entitySource == PyInterpreterLibraryEntitySource }.toList().forEach(storage::removeEntity)
      addInterpreterLibraries(storage, wanted, urlManager)
    }
  }

  /**
   * Gives the module of each Python project of [evoPyProjects] the source roots of its interpreter, under the content
   * root that holds each of them. A root that the module already has, from its `.iml` or as a content root, is left
   * alone. The roots that a module had from an earlier interpreter go away.
   */
  private suspend fun syncSourceRoots(evoPyProjects: Sequence<EvoPyProject>) {
    val workspaceModel = project.workspaceModel
    // Only the SDK updater writes source roots. Without it there are none, and a closing project creates no service.
    val roots = project.serviceIfCreated<PythonInterpreterDetails>()
    val storage = workspaceModel.currentSnapshot
    val wanted = mutableMapOf<ModuleId, Set<String>>()
    for (evoPyProject in evoPyProjects) {
      val interpreter = evoPyProject.interpreter ?: continue
      val module = evoPyProject.pyProject.residesOnModule.findModuleEntity(storage) ?: continue
      val sourceRoots = roots?.sourceRoots(PythonInterpreterKey(interpreter.ownerDir, interpreter.ref)).orEmpty()
      if (sourceRoots.isNotEmpty()) wanted[module.symbolicId] = sourceRoots.mapTo(LinkedHashSet()) { it.url }
    }
    if (currentSourceRoots(storage) == plannedSourceRoots(storage, wanted).mapValues { (_, planned) -> planned.keys }) return

    workspaceModel.update("Give the modules the source roots of their Python interpreters") { mutable ->
      for (root in mutable.entities<SourceRootEntity>().filter { it.entitySource == PyInterpreterLibraryEntitySource }.toList()) {
        mutable.removeEntity(root)
      }
      val urlManager = workspaceModel.getVirtualFileUrlManager()
      for ((_, planned) in plannedSourceRoots(mutable, wanted)) {
        for ((url, contentRoot) in planned) {
          mutable.modifyContentRootEntity(contentRoot) {
            // The default java source properties, as a root of an `.iml` has. Without them the root model reads other
            // properties than it builds, and the Project Structure page shows a change that is not there.
            sourceRoots += SourceRootEntity(urlManager.storeAndGet(url), JAVA_SOURCE_ROOT_TYPE, PyInterpreterLibraryEntitySource) {
              javaSourceRoots = listOf(JavaSourceRootPropertiesEntity(false, "", PyInterpreterLibraryEntitySource))
            }
          }
        }
      }
    }
  }

  /**
   * The source roots that [wanted] gives each module in [storage], each with the content root that holds it. A root
   * goes to a module only when the closest content root that holds it, among all modules, is one of this module: a path
   * inside a nested module, such as an editable package with its own `pyproject.toml`, belongs to that module. A content
   * root itself, and a source root that the module has from elsewhere, such as its `.iml`, are left out.
   */
  private fun plannedSourceRoots(storage: EntityStorage, wanted: Map<ModuleId, Set<String>>): Map<ModuleId, Map<String, ContentRootEntity>> {
    val contentRoots = storage.entities<ContentRootEntity>().toList()
    return wanted.mapNotNull { (moduleId, urls) ->
      val module = storage.resolve(moduleId) ?: return@mapNotNull null
      val planned = urls.mapNotNull { url ->
        val closest = contentRoots
          .filter { url == it.url.url || url.startsWith(it.url.url.trimEnd('/') + "/") }
          .maxByOrNull { it.url.url.length } ?: return@mapNotNull null
        if (closest.module.symbolicId != moduleId || closest.url.url == url) return@mapNotNull null
        val hasOther = closest.sourceRoots.any { it.url.url == url && it.entitySource != PyInterpreterLibraryEntitySource }
        if (hasOther) null else url to closest
      }.toMap()
      if (planned.isEmpty() || module.contentRoots.isEmpty()) null else moduleId to planned
    }.toMap()
  }

  /** The source roots that [syncSourceRoots] gave, by module. */
  private fun currentSourceRoots(storage: EntityStorage): Map<ModuleId, Set<String>> =
    storage.entities<SourceRootEntity>()
      .filter { it.entitySource == PyInterpreterLibraryEntitySource }
      .groupBy({ it.contentRoot.module.symbolicId }, { it.url.url })
      .mapValues { (_, urls) -> urls.toSet() }

  private fun addInterpreterLibraries(storage: MutableEntityStorage, wanted: Map<String, List<VirtualFile>>, urlManager: VirtualFileUrlManager) {
    for ((name, roots) in wanted) {
      storage.addEntity(LibraryEntity(
        name = name,
        tableId = LibraryTableId.ProjectLibraryTableId,
        roots = roots.map { LibraryRoot(urlManager.storeAndGet(it), LibraryRootTypeId.COMPILED) },
        entitySource = PyInterpreterLibraryEntitySource,
      ) {
        typeId = LibraryTypeId(PY_INTERPRETER_LIBRARY_KIND_ID)
      })
    }
  }

  /**
   * For tests: builds the interpreter libraries from the current snapshot now. A test that changes the roots of an
   * interpreter calls it, so the library holds the new roots before the test resolves.
   */
  @TestOnly
  suspend fun syncForTests() {
    sync(EvoPyProjectModel.getInstance(project).snapshot().evoPyProjects)
  }

  companion object {
    fun getInstance(project: Project): PyInterpreterLibrarySync = project.getService(PyInterpreterLibrarySync::class.java)
  }
}

private val JAVA_SOURCE_ROOT_TYPE: SourceRootTypeId = SourceRootTypeId("java-source")

/**
 * The URLs of the source roots that [module] has from the `sys.path` of its interpreter, see
 * [PythonInterpreterDetails.sourceRoots]. No `.iml` stores them, so the Project Structure page shows them, but does not let
 * the user change them: the next sync would give them back.
 */
@ApiStatus.Internal
fun interpreterSourceRootUrls(module: Module): Set<String> {
  val storage = module.project.workspaceModel.currentSnapshot
  val entity = module.findModuleEntity(storage) ?: return emptySet()
  return entity.contentRoots.flatMap { it.sourceRoots }
    .filter { it.entitySource == PyInterpreterLibraryEntitySource }
    .mapTo(HashSet()) { it.url.url }
}
