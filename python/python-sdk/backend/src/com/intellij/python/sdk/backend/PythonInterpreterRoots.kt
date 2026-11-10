// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.entities
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.python.sdk.getPathsToTransfer
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
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
 * It follows the changes of the facts. A contributor whose roots change asks for a new derive, see [requestDerive]. When
 * the roots of an interpreter change, it commits the class roots into the SDK of the interpreter, for the code that
 * reads the roots of an SDK, and reports the change on [changes]. A change that gives the same roots reports nothing.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class PythonInterpreterRoots(private val project: Project, scope: CoroutineScope) {
  /** The last roots of each interpreter, which [changes] reported. */
  private val roots = ConcurrentHashMap<PythonInterpreterKey, PythonInterpreterRootFiles>()
  private val changeCount = MutableStateFlow(0L)
  /** The interpreters whose roots the writer derives and commits next, see [requestDerive]. */
  private val pending = ConcurrentHashMap<PythonInterpreterKey, PythonInterpreter>()
  /** The number of the last [requestDerive]. A new number wakes the writer. */
  private val requests = MutableStateFlow(0L)
  /** The number of the last request whose roots the writer committed, see [awaitCommittedForTests]. */
  private val committed = MutableStateFlow(0L)

  init {
    scope.launch {
      // A change that names no interpreter, such as a commit of an SDK of the SDK table, can change any interpreter. A
      // change that names one comes from [PythonInterpreterDetails.writeFacts], which asks for its roots itself.
      PythonInterpreterDetails.getInstance(project).changes.collect { key ->
        if (key == null) requestDeriveForAll()
      }
    }
    scope.launch(Dispatchers.Default) {
      // A state flow conflates the requests: a burst during a pass gives one more pass.
      requests.collect { writePending() }
    }
  }

  /**
   * A value for each change of the roots of an interpreter. It is a state, not an event: a collector that starts after
   * a change still gets it. The first value comes at once.
   */
  val changes: Flow<Unit> get() = changeCount.map { }

  /**
   * Asks the writer to derive the roots of [interpreter] again and to commit them into its SDK, and does not wait. A
   * change of the facts asks for it, and so does a contributor whose roots changed, such as after a package install. The
   * caller can hold a lock, so the derive does not run in its thread.
   *
   * One writer of the project derives and commits the roots of the interpreters, one at a time. So no two commits race,
   * and the last commit comes from the newest input. Nobody waits for the writer, so a commit that waits until a modal
   * progress ends blocks nobody (PY-93878).
   */
  fun requestDerive(interpreter: PythonInterpreter) {
    pending[interpreter.key] = interpreter
    requests.update { it + 1 }
  }

  /** For tests: suspends until the writer committed the roots of every request, see [requestDerive]. */
  @TestOnly
  suspend fun awaitCommittedForTests() {
    val last = requests.value
    committed.first { it >= last }
  }

  /**
   * Derives and commits the roots of each pending interpreter, see [requestDerive]. The writer takes an interpreter out
   * of [pending] before it derives, so a request during the derive asks for one more pass. A request puts its interpreter
   * into [pending] before it takes its number, so each request up to [last] is in this pass or in an earlier one.
   */
  private suspend fun writePending() {
    val last = requests.value
    while (true) {
      val key = pending.keys.firstOrNull() ?: break
      val interpreter = pending.remove(key) ?: continue
      val derived = derive(interpreter)
      // No write action when nothing changes. A write action on the EDT, not a background one: a pending background
      // write stops the read actions, such as of a VFS refresh, that a caller holding the write-intent lock can wait for.
      if (needsCommit(interpreter, derived)) edtWriteAction { commit(interpreter, derived) }
      else store(interpreter, derived)
    }
    committed.value = last
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
   * Drops the roots of every interpreter whose key is not in [keys]. The registry calls it after each recompute. A pending
   * request stays: it holds its interpreter, and a recompute can drop a key for a moment.
   */
  internal fun retain(keys: Set<PythonInterpreterKey>) {
    roots.keys.retainAll(keys)
  }

  /**
   * Stores [derived] as the roots of [interpreter] and commits them into its SDK. Runs in a write action: the legacy flow
   * reads the class roots of the facts here, so a commit never restores the facts that a write replaced.
   *
   * The SDK of the interpreter library flow gets the class roots, because it holds no fact. The SDK of the legacy flow
   * holds the facts in its roots and in its paths to transfer, see [PythonInterpreterDetails]. It keeps the class roots
   * of the facts, and gets the other class roots after them. A path that it transfers to the module is no class root of
   * the SDK, else the module is a library, and the editor opens its files in reader mode (PY-93817).
   */
  private fun commit(interpreter: PythonInterpreter, derived: PythonInterpreterRootFiles) {
    val sdk = interpreter.sdk
    commitSdk(sdk, sdk.versionString, sdkClassRoots(interpreter, derived))
    if (isInterpreterLibraryFlowEnabled()) {
      // The SDK of the interpreter library flow holds no fact, but the code that reads an SDK, such as the `PYTHONPATH`
      // of a run, reads the user paths from it. Nothing reads them back.
      commitUserPaths(sdk, PythonInterpreterDetails.getInstance(project).facts(interpreter))
    }
    store(interpreter, derived)
  }

  /** Stores [derived] as the roots of [interpreter], and reports a change when they differ from the ones before. */
  private fun store(interpreter: PythonInterpreter, derived: PythonInterpreterRootFiles) {
    val before = roots.put(interpreter.key, derived)
    if (before != derived) changeCount.update { it + 1 }
  }

  /** Whether the SDK of [interpreter] holds other class roots than [derived] gives, or other user paths, see [commit]. */
  private fun needsCommit(interpreter: PythonInterpreter, derived: PythonInterpreterRootFiles): Boolean {
    val sdk = interpreter.sdk
    if (sdkClassRoots(interpreter, derived) != PythonSdkUtil.getPythonClassRoots(sdk).toList()) return true
    return isInterpreterLibraryFlowEnabled() && !hasUserPaths(sdk, PythonInterpreterDetails.getInstance(project).facts(interpreter))
  }

  /**
   * The class roots that the SDK of [interpreter] gets from [derived]. The SDK of the interpreter library flow holds no
   * fact. The SDK of the legacy flow keeps the class roots of its facts, without the paths that it transfers to the module.
   */
  private fun sdkClassRoots(interpreter: PythonInterpreter, derived: PythonInterpreterRootFiles): List<VirtualFile> {
    if (isInterpreterLibraryFlowEnabled()) return derived.classRoots
    val sdk = interpreter.sdk
    val transferred = getPathsToTransfer(sdk)
    val factRoots = PythonSdkUtil.getPythonClassRoots(sdk).filterNot(::isDerivedRoot)
    return (factRoots + derived.classRoots).filter { it !in transferred }.distinct()
  }

  private fun rootsOf(interpreter: PythonInterpreter): PythonInterpreterRootFiles =
    roots[interpreter.key] ?: derive(interpreter).also { roots.putIfAbsent(interpreter.key, it) }

  /** Asks for a derive of every interpreter that has roots, after a change of the facts that names no interpreter. */
  private fun requestDeriveForAll() {
    // The iterator of the map skips a key that [retain] removes on another thread. A copy of the keys can throw then.
    for (key in roots.keys) interpreterOf(key)?.let(::requestDerive)
  }

  private fun interpreterOf(key: PythonInterpreterKey): PythonInterpreter? =
    PythonInterpreterRegistry.getInstance(project).resolveIfReady(key)

  private fun derive(interpreter: PythonInterpreter): PythonInterpreterRootFiles {
    val facts = PythonInterpreterDetails.getInstance(project).facts(interpreter)
    val [sourcePaths, classPaths] = splitFactRootPaths(interpreter, facts)
    val excluded = facts.excludedPaths.mapTo(HashSet(), ::canonicalPath)
    // A contributed root, such as the skeletons under a content root, is always a class root (PY-90400).
    val contributed = PythonInterpreterRootsContributor.EP_NAME.extensionList
      .flatMap { it.classRoots(project, interpreter) }
      .map { it.normalize() }
      .filter { it !in excluded }
    val classRoots = (classPaths + contributed).distinct().mapNotNull(::toRoot)
    return PythonInterpreterRootFiles(classRoots, sourcePaths.mapNotNullTo(LinkedHashSet(), ::toRoot))
  }

  /**
   * The root paths of [facts], see [factRootPaths], split in two: the paths inside the owner module of [interpreter],
   * which are source roots of the module, and the other paths, which are class roots. A path under the environment of
   * [interpreter] or under an excluded folder of the module is a class root, such as an environment inside the project
   * (PY-17241, PY-18970).
   */
  internal fun splitFactRootPaths(interpreter: PythonInterpreter, facts: PythonInterpreterFacts): Pair<List<Path>, List<Path>> {
    // The paths are compared as the file system names them: a link, such as `/var` on macOS, leads to one path only.
    val canonical = facts.copy(
      sysPath = facts.sysPath.map(::canonicalPath),
      addedPaths = facts.addedPaths.map(::canonicalPath),
      excludedPaths = facts.excludedPaths.mapTo(HashSet(), ::canonicalPath),
    )
    val home = interpreter.detectedHomePath?.let(::canonicalPath)
    val module = ownerModule(interpreter)
    val moduleRoots = ownerModuleRoots(interpreter).map { it.normalize() }
    val moduleExcluded = module?.contentRoots.orEmpty().flatMap { it.excludedUrls }.map { canonicalPath(it.url.toPath()).normalize() }
    return factRootPaths(canonical, home).partition { path ->
      moduleRoots.any { path.startsWith(it) } &&
      (home == null || !path.startsWith(home)) &&
      moduleExcluded.none { path.startsWith(it) }
    }
  }

  /**
   * The content and source roots of the module whose content root is the owner directory of [interpreter], or the owner
   * directory alone when this project has no such module. A path inside them is a source root of the module.
   */
  internal fun ownerModuleRoots(interpreter: PythonInterpreter): Set<Path> {
    val module = ownerModule(interpreter) ?: return setOf(interpreter.ownerDir.normalize())
    return module.contentRoots.flatMap { root -> listOf(root.url) + root.sourceRoots.map { it.url } }.mapTo(HashSet()) { it.toPath() }
  }

  /** The module whose content root is the owner directory of [interpreter], or `null` when this project has none. */
  private fun ownerModule(interpreter: PythonInterpreter): ModuleEntity? {
    val ownerDir = interpreter.ownerDir.normalize()
    return project.workspaceModel.currentSnapshot.entities<ModuleEntity>()
      .firstOrNull { entity -> entity.contentRoots.any { it.url.toPath().normalize() == ownerDir } }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PythonInterpreterRoots = project.service()
  }
}
