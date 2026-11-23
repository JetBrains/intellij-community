// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.evolution

import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.python.sdk.backend.classRoots
import com.intellij.java.workspace.entities.javaSourceRoots
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.storeAndGet
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.facet.FacetType
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.FacetEntityTypeId
import com.intellij.platform.workspace.jps.entities.modifyFacetEntity
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryPropertiesEntity
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.LibraryTypeId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.libraryProperties
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.platform.workspace.jps.entities.modifyContentRootEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.python.sdk.backend.PY_INTERPRETER_LIBRARY_KIND_ID
import com.jetbrains.python.sdk.internal.PYTHON_FACET_ID
import com.intellij.python.sdk.backend.PyInterpreterLibraryName
import com.intellij.python.sdk.backend.PyInterpreterLibraryProperties
import com.intellij.python.sdk.backend.PythonInterpreterRoots
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.isInterpreterLibraryFlowEnabled
import com.intellij.python.sdk.backend.pyInterpreterDependency
import com.intellij.python.sdk.backend.sitePackagesToExclude
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterDetails
import com.intellij.python.sdk.backend.PythonInterpreterFacts
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * The source of the interpreter libraries, see [PyInterpreterLibraryName], and of the source roots that a module gets
 * from the `sys.path` of its interpreter, see [PythonInterpreterRoots.sourceRoots]. It is no file, so the workspace
 * model never saves them: no `.iml` stores these roots, they hold local paths, and live only in the cache of this
 * machine. [PyInterpreterLibrarySync] builds them again from the interpreters.
 */
internal object PyInterpreterLibraryEntitySource : EntitySource

/**
 * Builds the interpreter library of each Python project, and the source roots that its module gets from the `sys.path`
 * of the interpreter, see [PythonInterpreterRoots.sourceRoots].
 *
 * A module stores only the name of its interpreter library. The library itself holds the roots of the interpreter, and
 * is not stored, see [PyInterpreterLibraryEntitySource]. So this service builds it again. [EvoPyProjectModel] calls
 * [sync] in each pass, before it publishes the snapshot, so the snapshot never names a library without roots. The
 * service also calls it when the roots of an interpreter change, which no snapshot reports.
 *
 * The libraries of the project are replaced together, so a library that no module names any more goes away.
 *
 * The user can edit an interpreter library on the Libraries page of a Java IDE. The edit goes into the facts of its
 * interpreter, as the paths dialog of PyCharm writes them, see [factsAfterLibraryEdit]. The library is then built again
 * from the facts, so the edit stays after a restart.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class PyInterpreterLibrarySync(private val project: Project, private val scope: CoroutineScope) {
  /**
   * The library states that this service wrote lately, by library name, so an edit of the user tells from its own writes.
   * Syncs run in parallel, and an event of the event log can come after a newer write, so each name keeps a short history.
   * Only `synchronized` blocks guard it, and none of them suspends: a sync under a modal progress and a sync outside it
   * must not wait for each other, or the write action of one waits for the modal progress of the other.
   */
  private val written = HashMap<String, WrittenLibrary>()

  internal fun start() {
    scope.launch {
      // The roots of an interpreter change with its facts and its contributors, which no snapshot reports.
      PythonInterpreterRoots.getInstance(project).changes
        .conflate()
        .collect { sync(EvoPyProjectModel.getInstance(project).snapshotOrNull()?.evoPyProjects ?: return@collect) }
    }
    scope.launch {
      // An edit on the Libraries page changes a library that this service wrote. Its own writes change nothing here.
      project.workspaceModel.eventLog.collect { event ->
        val touched = event.getChanges(LibraryEntity::class.java).any { change ->
          (change.newEntity ?: change.oldEntity)?.entitySource == PyInterpreterLibraryEntitySource
        }
        if (touched && isInterpreterLibraryFlowEnabled()) applyUserEdits(takeUserEdits(currentLibraries(event.storageAfter)))
      }
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
    syncFacets()
  }

  /**
   * Gives each module that names an interpreter the Python facet in memory, see [inMemoryFacetChanges], so Project
   * Structure shows it as a Python module, and the `.iml` keeps only the interpreter library entry. A facet that the user
   * adds with "+" is saved by Project Structure, and moves to memory when the module gets its interpreter. Only an IDE
   * with the Python facet type has it: PyCharm has none.
   */
  private suspend fun syncFacets() {
    if (FacetType.EP_NAME.extensionList.none { it.stringId == PYTHON_FACET_ID }) return
    val workspaceModel = project.workspaceModel
    val planned = inMemoryFacetChanges(workspaceModel.currentSnapshot, PyInterpreterLibraryEntitySource)
    if (planned.modulesToAdd.isEmpty() && planned.facetsToRemove.isEmpty() && planned.facetsToMove.isEmpty()) return
    workspaceModel.update("Give the Python modules their Python facet") { storage ->
      // Planned again on the storage of the update, which can be newer than the snapshot above.
      val changes = inMemoryFacetChanges(storage, PyInterpreterLibraryEntitySource)
      changes.facetsToRemove.forEach(storage::removeEntity)
      for (facet in changes.facetsToMove) storage.modifyFacetEntity(facet) { entitySource = PyInterpreterLibraryEntitySource }
      for (module in changes.modulesToAdd) {
        storage.modifyModuleEntity(module) {
          moduleSettings += FacetEntity(PYTHON_FACET_NAME, FacetEntityTypeId(PYTHON_FACET_ID), PyInterpreterLibraryEntitySource) {
            configurationXmlTag = IN_MEMORY_FACET_CONFIGURATION
          }
        }
      }
    }
  }

  private suspend fun syncLibraries(evoPyProjects: Sequence<EvoPyProject>) {
    val workspaceModel = project.workspaceModel
    val urlManager = workspaceModel.getVirtualFileUrlManager()
    val storage = workspaceModel.currentSnapshot
    val classRoots = mutableMapOf<String, List<VirtualFileUrl>>()
    val interpreters = mutableMapOf<String, PythonInterpreter>()
    for (evoPyProject in evoPyProjects) {
      val interpreter = evoPyProject.interpreter ?: continue
      val name = evoPyProject.pyProject.residesOnModule.findModuleEntity(storage)?.dependencies?.pyInterpreterDependency?.library?.name ?: continue
      classRoots[name] = interpreter.classRoots.map { urlManager.storeAndGet(it) }
      interpreters[name] = interpreter
    }
    // The exclusion is the same for the whole project, so a directory that any interpreter here needs stays.
    val kept = classRoots.values.flatten().toSet()
    val details = PythonInterpreterDetails.getInstance(project)
    val wanted = classRoots.mapValues { (name, roots) ->
      val folders = details.facts(interpreters.getValue(name)).excludedFolders.map { urlManager.storeAndGet(pathToUrl(it)) }
      LibraryRoots(roots, (sitePackagesToExclude(roots, kept) + folders).distinct(), PyInterpreterLibraryName.parse(name)?.let(PyInterpreterLibraryProperties::xmlTagOf))
    }

    val current = currentLibraries(storage)
    // An edit that the event log has not given yet goes into the facts before this write replaces it.
    applyUserEdits(takeUserEdits(current))
    // Before the write, so its event finds the new state among the states of this service.
    remember(wanted, interpreters)
    if (current == wanted) return

    workspaceModel.update("Build the Python interpreter libraries") { storage ->
      // Only the libraries: the source roots of the same source stay, see [syncSourceRoots].
      storage.entities<LibraryEntity>().filter { it.entitySource == PyInterpreterLibraryEntitySource }.toList().forEach(storage::removeEntity)
      addInterpreterLibraries(storage, wanted)
    }
  }

  /**
   * The roots of an interpreter library: the class roots of its interpreter, and the `site-packages` that the project
   * excludes, see [sitePackagesToExclude]. The platform excludes them for the project, see `ExcludedRootFileIndexContributor`.
   * [propertiesTag] names the tool of the interpreter, so the Libraries page draws its icon, see [PyInterpreterLibraryProperties].
   */
  private data class LibraryRoots(val classRoots: List<VirtualFileUrl>, val excludedRoots: List<VirtualFileUrl>, val propertiesTag: String?) {
    fun paths(): InterpreterLibraryPaths =
      InterpreterLibraryPaths(classRoots.map(::pathOf), excludedRoots.mapTo(HashSet(), ::pathOf))
  }

  /** The states that [syncLibraries] wrote lately for the library of [interpreter], the newest last. */
  private class WrittenLibrary(val interpreter: PythonInterpreter) {
    val states: ArrayDeque<LibraryRoots> = ArrayDeque()

    fun add(roots: LibraryRoots) {
      states.remove(roots)
      states.addLast(roots)
      while (states.size > WRITTEN_STATES) states.removeFirst()
    }
  }

  /** An edit of the user: the library of [interpreter] went from [from], the last state that this service wrote, to [to]. */
  private class UserEdit(val interpreter: PythonInterpreter, val from: LibraryRoots, val to: LibraryRoots)

  /** Adds [wanted] to the states that this service wrote. A library of another interpreter starts a new history. */
  private fun remember(wanted: Map<String, LibraryRoots>, interpreters: Map<String, PythonInterpreter>) {
    synchronized(written) {
      for ((name, roots) in wanted) {
        val interpreter = interpreters.getValue(name)
        val library = written[name]?.takeIf { it.interpreter == interpreter } ?: WrittenLibrary(interpreter).also { written[name] = it }
        library.add(roots)
      }
    }
  }

  /**
   * The libraries of [current] in a state that this service did not write: the edits of the user. Each edit becomes the
   * newest state of its library, so it is taken once.
   */
  private fun takeUserEdits(current: Map<String, LibraryRoots>): List<UserEdit> =
    synchronized(written) {
      current.mapNotNull { (name, roots) ->
        val library = written[name] ?: return@mapNotNull null
        if (roots in library.states) return@mapNotNull null
        val edit = UserEdit(library.interpreter, library.states.last(), roots)
        library.add(roots)
        edit
      }
    }

  /** The interpreter libraries of [storage], by name. */
  private fun currentLibraries(storage: EntityStorage): Map<String, LibraryRoots> =
    storage.entities<LibraryEntity>()
      .filter { it.entitySource == PyInterpreterLibraryEntitySource }
      .associate {
        it.name to LibraryRoots(it.roots.map { root -> root.url }, it.excludedRoots.map { excluded -> excluded.url }, it.libraryProperties?.propertiesXmlTag)
      }

  /**
   * Writes each of [edits] into the facts of its interpreter, see [factsAfterLibraryEdit]. The new facts derive new roots,
   * and the next sync builds the library from them.
   */
  private suspend fun applyUserEdits(edits: List<UserEdit>) {
    val details = PythonInterpreterDetails.getInstance(project)
    for (edit in edits) {
      val facts = details.facts(edit.interpreter)
      val after = factsAfterLibraryEdit(facts, edit.from.paths(), edit.to.paths())
      if (after != facts) details.writeFacts(edit.interpreter, after)
    }
  }

  /**
   * Gives the module of each Python project of [evoPyProjects] the source roots of its interpreter, under the content
   * root that holds each of them. A root that the module already has, from its `.iml` or as a content root, is left
   * alone. The roots that a module had from an earlier interpreter go away.
   */
  private suspend fun syncSourceRoots(evoPyProjects: Sequence<EvoPyProject>) {
    val workspaceModel = project.workspaceModel
    // A closing project creates no service.
    val roots = project.serviceIfCreated<PythonInterpreterRoots>()
    val storage = workspaceModel.currentSnapshot
    val wanted = mutableMapOf<ModuleId, Set<String>>()
    for (evoPyProject in evoPyProjects) {
      val interpreter = evoPyProject.interpreter ?: continue
      val module = evoPyProject.pyProject.residesOnModule.findModuleEntity(storage) ?: continue
      val sourceRoots = roots?.sourceRoots(interpreter).orEmpty()
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

  private fun addInterpreterLibraries(storage: MutableEntityStorage, wanted: Map<String, LibraryRoots>) {
    for ((name, roots) in wanted) {
      storage.addEntity(LibraryEntity(
        name = name,
        tableId = LibraryTableId.ProjectLibraryTableId,
        roots = roots.classRoots.map { LibraryRoot(it, LibraryRootTypeId.COMPILED) },
        entitySource = PyInterpreterLibraryEntitySource,
      ) {
        typeId = LibraryTypeId(PY_INTERPRETER_LIBRARY_KIND_ID)
        excludedRoots = roots.excludedRoots.map { ExcludeUrlEntity(it, PyInterpreterLibraryEntitySource) }
        roots.propertiesTag?.let { tag ->
          libraryProperties = LibraryPropertiesEntity(PyInterpreterLibraryEntitySource) { propertiesXmlTag = tag }
        }
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
 * [PythonInterpreterRoots.sourceRoots]. No `.iml` stores them, so the Project Structure page shows them, but does not let
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

/** The roots of an interpreter library as paths: its class roots and the folders that it excludes. */
internal data class InterpreterLibraryPaths(val classRoots: List<Path>, val excludedFolders: Set<Path>)

/**
 * The [facts] of an interpreter after the user edited its library from [written] to [edited], as the paths dialog of
 * PyCharm writes them:
 * - An added root leaves the excluded paths, or else it becomes an added path.
 * - A removed root leaves the added paths, or else it becomes an excluded path.
 * - A newly excluded folder becomes an excluded folder, and a folder that is no longer excluded leaves them.
 */
internal fun factsAfterLibraryEdit(
  facts: PythonInterpreterFacts,
  written: InterpreterLibraryPaths,
  edited: InterpreterLibraryPaths,
): PythonInterpreterFacts {
  val addedPaths = facts.addedPaths.toMutableList()
  val excludedPaths = facts.excludedPaths.toMutableSet()
  for (root in edited.classRoots - written.classRoots.toSet()) {
    if (!excludedPaths.remove(root)) addedPaths.add(root)
  }
  for (root in written.classRoots - edited.classRoots.toSet()) {
    if (!addedPaths.remove(root)) excludedPaths.add(root)
  }
  val excludedFolders = facts.excludedFolders + (edited.excludedFolders - written.excludedFolders) - (written.excludedFolders - edited.excludedFolders)
  return facts.copy(addedPaths = addedPaths.distinct(), excludedPaths = excludedPaths, excludedFolders = excludedFolders)
}

/** How many states of a library [PyInterpreterLibrarySync] keeps, see its `written`. A few syncs run at a time at most. */
private const val WRITTEN_STATES = 8

private fun pathOf(url: VirtualFileUrl): Path = Path.of(VfsUtilCore.urlToPath(url.url))

private fun pathToUrl(path: Path): String = VfsUtilCore.pathToUrl(path.invariantSeparatorsPathString)
