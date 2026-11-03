// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.entities
import com.intellij.workspaceModel.ide.toPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** The class roots and the source roots of an interpreter, as files, see [PythonInterpreterRoots]. */
@ApiStatus.Internal
data class PythonInterpreterRootFiles(val classRoots: List<VirtualFile>, val sourceRoots: Set<VirtualFile>)

/**
 * The class roots and the source roots of the interpreters of this project, in both flows. It derives them from the
 * facts, see [PythonInterpreterDetails], and the roots of each [PythonInterpreterRootsContributor], such as the skeletons,
 * the typeshed stdlib and the stubs of the installed packages, see [factRootPaths].
 *
 * It follows the changes of the facts. A contributor whose roots change asks for a new derive, see [requestRefresh]. When
 * the roots of an interpreter change, it commits the class roots into the SDK of the interpreter, for the code that
 * reads the roots of an SDK, and reports the change on [changes]. A change that gives the same roots reports nothing.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class PythonInterpreterRoots(private val project: Project, private val scope: CoroutineScope) {
  /** The last roots of each interpreter, which [changes] reported. */
  private val roots = ConcurrentHashMap<PythonInterpreterKey, PythonInterpreterRootFiles>()
  private val changeCount = MutableStateFlow(0L)
  /** The refreshes that [requestRefresh] started and that did not end yet, see [awaitRefreshedForTests]. */
  private val pendingRefreshes = MutableStateFlow(0)

  init {
    scope.launch {
      // A write of the details refreshes its interpreter itself. This follows a change that no write of the details
      // made, such as a commit of an SDK of the SDK table.
      PythonInterpreterDetails.getInstance(project).changes.collect { key -> if (key == null) refreshAll() }
    }
  }

  /**
   * A value for each change of the roots of an interpreter. It is a state, not an event: a collector that starts after
   * a change still gets it. The first value comes at once.
   */
  val changes: Flow<Unit> get() = changeCount.map { }

  /**
   * Derives the roots of [interpreter] again on a background thread, see [refresh]. For a contributor whose roots changed,
   * such as after a package install. The caller can hold a lock, so the refresh does not run in its thread.
   */
  fun requestRefresh(interpreter: PythonInterpreter) {
    pendingRefreshes.update { it + 1 }
    scope.launch(Dispatchers.Default) {
      try {
        refresh(interpreter)
      }
      finally {
        pendingRefreshes.update { it - 1 }
      }
    }
  }

  /** For tests: suspends until every refresh that [requestRefresh] started has ended. */
  @TestOnly
  suspend fun awaitRefreshedForTests() {
    pendingRefreshes.first { it == 0 }
  }

  /** For tests: how many changes this service has reported, see [changes]. */
  @get:TestOnly
  val changeCountForTests: Long get() = changeCount.value

  /**
   * The class roots of [interpreter]: `sys.path`, the skeletons, the added paths, the typeshed stdlib and the contributed
   * roots.
   */
  fun classRoots(interpreter: PythonInterpreter): List<VirtualFile> = rootsOf(interpreter).classRoots

  /**
   * The source roots of [interpreter]: the directories of `sys.path` and the added paths that are inside its owner
   * module, but not inside the environment. The interpreter library flow gives them to the module, which no `.iml`
   * stores. The legacy flow writes them to the `.iml`.
   */
  fun sourceRoots(interpreter: PythonInterpreter): Set<VirtualFile> = rootsOf(interpreter).sourceRoots

  /**
   * Derives the roots of [interpreter] again, commits them into its SDK, and reports a change when they differ from the
   * ones before. A test calls it after it changes what a contributor gives, so the roots change before it goes on.
   *
   * The SDK of the interpreter library flow gets the class roots, because it holds no fact. The SDK of the legacy flow
   * holds the facts in its roots, so it keeps every root of the facts, also a source root of the module, and gets the
   * other class roots after them. Else a source root would vanish from the facts.
   */
  fun refresh(interpreter: PythonInterpreter) {
    val derived = derive(interpreter)
    val before = roots.put(interpreter.key, derived)
    val facts = PythonInterpreterDetails.getInstance(project).facts(interpreter)
    if (isInterpreterLibraryFlowEnabled()) {
      commitSdk(interpreter.sdk, interpreter.sdk.versionString, derived.classRoots)
      // The SDK of the interpreter library flow holds no fact, but the code that reads an SDK, such as the `PYTHONPATH`
      // of a run, reads the user paths from it. Nothing reads them back.
      commitUserPaths(interpreter.sdk, facts)
    }
    else {
      val factRoots = sdkFactRoots(facts)
      commitSdk(interpreter.sdk, interpreter.sdk.versionString, (factRoots + derived.classRoots).distinct())
    }
    if (before != derived) changeCount.update { it + 1 }
  }

  /** Drops the roots of every interpreter whose key is not in [keys]. The registry calls it after each recompute. */
  internal fun retain(keys: Set<PythonInterpreterKey>) {
    roots.keys.retainAll(keys)
  }

  private fun rootsOf(interpreter: PythonInterpreter): PythonInterpreterRootFiles =
    roots[interpreter.key] ?: derive(interpreter).also { roots.putIfAbsent(interpreter.key, it) }

  /** Checks every interpreter that has roots, after a change of the facts that names no interpreter. */
  private fun refreshAll() {
    for (key in roots.keys.toList()) interpreterOf(key)?.let(::refresh)
  }

  private fun interpreterOf(key: PythonInterpreterKey): PythonInterpreter? =
    PythonInterpreterRegistry.getInstance(project).resolveIfReady(key)

  private fun derive(interpreter: PythonInterpreter): PythonInterpreterRootFiles {
    val facts = PythonInterpreterDetails.getInstance(project).facts(interpreter)
    // The paths are compared as the file system names them: a link, such as `/var` on macOS, leads to one path only.
    val canonical = facts.copy(
      sysPath = facts.sysPath.map(::canonicalPath),
      addedPaths = facts.addedPaths.map(::canonicalPath),
      excludedPaths = facts.excludedPaths.mapTo(HashSet(), ::canonicalPath),
    )
    val home = interpreter.detectedHomePath?.let(::canonicalPath)
    // A path of the facts inside the module and outside the environment is a source root of the module.
    val moduleRoots = ownerModuleRoots(interpreter).map { it.normalize() }
    val [sourcePaths, classPaths] = factRootPaths(canonical, home).partition { path ->
      moduleRoots.any { path.startsWith(it) } && (home == null || !path.startsWith(home))
    }
    // A contributed root, such as the skeletons under a content root, is always a class root (PY-90400).
    val contributed = PythonInterpreterRootsContributor.EP_NAME.extensionList
      .flatMap { it.classRoots(project, interpreter) }
      .map { it.normalize() }
      .filter { it !in canonical.excludedPaths }
    val classRoots = (classPaths + contributed).distinct().mapNotNull(::toRoot)
    return PythonInterpreterRootFiles(classRoots, sourcePaths.mapNotNullTo(LinkedHashSet(), ::toRoot))
  }

  /**
   * The content and source roots of the module whose content root is the owner directory of [interpreter], or the owner
   * directory alone when this project has no such module. A path inside them is a source root of the module.
   */
  private fun ownerModuleRoots(interpreter: PythonInterpreter): Set<Path> {
    val ownerDir = interpreter.ownerDir.normalize()
    val module = project.workspaceModel.currentSnapshot.entities<ModuleEntity>()
      .firstOrNull { entity -> entity.contentRoots.any { it.url.toPath().normalize() == ownerDir } } ?: return setOf(ownerDir)
    return module.contentRoots.flatMap { root -> listOf(root.url) + root.sourceRoots.map { it.url } }.mapTo(HashSet()) { it.toPath() }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PythonInterpreterRoots = project.service()
  }
}
