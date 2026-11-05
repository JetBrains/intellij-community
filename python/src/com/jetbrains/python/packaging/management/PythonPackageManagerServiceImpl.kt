// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.sdk.backend.PythonEnvironmentKey
import com.intellij.python.sdk.backend.PythonEnvironmentWatchers
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterUpdateRequest
import com.intellij.python.sdk.backend.PythonInterpreterUpdater
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.sdk.backend.isDisposed
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.IncorrectOperationException
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import com.jetbrains.python.packaging.requirementsTxt.PythonRequirementTxtSdkUtils
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class PythonPackageManagerServiceImpl(
  private val project: Project,
  private val scope: CoroutineScope,
) : PythonPackageManagerService, Disposable {
  /** One entry for each interpreter. Two interpreters of one SDK, with two owners, have two managers. */
  private val cache = ConcurrentHashMap<PythonInterpreter, CachedManager>()

  /** Guards the watcher of every entry, because a root change and a first call can reconcile at the same moment. */
  private val watchersLock = ReentrantLock()

  init {
    // A provider that comes or goes can build another manager for an interpreter, so the managers it replaces go.
    PythonPackageManagerProvider.EP_NAME.addChangeListener(scope) { dropAllManagers() }
    // The structure of the project, and not the workspace model it is derived from: a model listener reports the
    // change earlier, while `EvoPyProjectModel` still holds the generation before it, and reading that one would
    // reconcile against the interpreters of a moment that has passed.
    scope.launch {
      EvoPyProjectModel.getInstance(project).snapshotFlow().collect { snapshot ->
        syncWatchers(snapshot)
        startManagers(snapshot)
      }
    }
  }

  /**
   * Starts the manager of each interpreter of [snapshot], so its installed packages, and the derived roots that follow
   * them, are there right after the start, see [com.jetbrains.python.packaging.PythonInstalledPackagesCache]. A manager whose environment did not
   * change since its last list runs no process.
   */
  private fun startManagers(snapshot: EvoPyProjectModel.Snapshot) {
    // A snapshot can still hold an interpreter whose SDK is gone, and such an interpreter gets no manager.
    for (interpreter in snapshot.interpreters.filterNot { it.isDisposed }) {
      forPythonInterpreter(project, interpreter).startInit()
    }
  }

  /** One cached manager, and the watcher of the interpreter paths that it has while the project uses the interpreter. */
  private class CachedManager(val interpreter: PythonInterpreter, val manager: PythonPackageManager) {
    var watcher: Disposable? = null

    /** Whether [watcher] has its subscription. The key of the environment can come later, see [watchInterpreterPaths]. */
    @Volatile
    var subscribed: Boolean = false

    /** Removes this entry when its SDK goes, see [removeOnSdkDisposal]. A dropped entry disposes it, so none piles up. */
    var removal: Disposable? = null

    /** Whether the manager of this entry still matches its SDK, see [PythonPackageManager.matchesSdk]. */
    fun isCurrent(): Boolean = manager.matchesSdk()

    /** The SDK of [interpreter], for the watcher and the SDK updater, which still take one. */
    @Suppress("DEPRECATION")
    val sdk: Sdk get() = interpreter.getSdkAPI()
  }

  /**
   * Gives a watcher of the interpreter paths to each entry that the project uses, and takes it from each entry that it
   * does not use any more.
   *
   * A watcher asks for a full update of its interpreter on a change, and that update starts the interpreter for
   * skeleton generation and for a package scan. An interpreter that no module uses must not ask for that, see
   * PY-88315. A user who opens the interpreter settings looks at interpreters that the project does not use, and one
   * of those becomes the interpreter of a module later, so both directions of the move happen.
   *
   * The manager itself is never disposed here. A caller holds the manager it was given and runs its work on the scope
   * of that manager, and a change to a whole other module must not cancel that work.
   *
   * Called on each [structure] the model publishes, and after a new entry is cached, so whichever of the two runs last
   * leaves the entry right. A `null` [structure] is the moment before the first one lands, and the flow brings it.
   *
   * Reads a set and registers a file listener, so it costs little. A cache hit does not call it: that would put the
   * call on every read of [com.jetbrains.python.packaging.bridge.PythonPackageManagementServiceBridge.manager], which is a property.
   */
  private fun syncWatchers(structure: EvoPyProjectModel.Snapshot?) {
    if (cache.isEmpty() || structure == null) return
    val interpretersInUse = structure.interpreters
    watchersLock.withLock {
      for ([key, entry] in cache) {
        val watched = entry.watcher != null
        val inUse = entry.interpreter in interpretersInUse
        if (inUse && !watched) {
          watchInterpreterPaths(key, entry)
        }
        else if (!inUse && watched) {
          logger.info("The project does not use '${entry.sdk.name}' any more, so its paths are not watched")
          entry.watcher?.let { Disposer.dispose(it) }
          entry.watcher = null
          entry.subscribed = false
        }
      }
    }
  }

  /** Always call under [watchersLock]. */
  private fun watchInterpreterPaths(key: PythonInterpreter, entry: CachedManager) {
    val watcher = Disposer.newDisposable("VFS listener for ${entry.sdk.name} in scope of ${project.name}")
    try {
      // Under the interpreter, so the watch goes with it. The manager outlives the interpreter, so it cannot be that parent.
      Disposer.register(entry.sdk as? Disposable ?: entry.manager, watcher)
    }
    catch (e: IncorrectOperationException) {
      logger.info("The interpreter '${entry.sdk.name}' is gone, dropping its manager", e)
      cache.remove(key, entry)
      return
    }
    entry.watcher = watcher
    // One listener for each environment, for every project that runs it, see PythonEnvironmentWatchers. The key of a
    // WSL or Docker environment needs its machine resolved, as the facts that the listener reads are stored under that
    // key, so the watch starts when the key is known. A watcher that goes before that watches nothing.
    scope.launch(NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
      // An SDK that names no binary has no paths to watch.
      val environment = PythonEnvironmentKey.of(entry.sdk) ?: return@launch
      watchersLock.withLock {
        if (entry.watcher !== watcher) return@launch
        PythonEnvironmentWatchers.getInstance().watch(environment, watcher) {
          // A background request: a change under the paths of an interpreter that the project does not use must not
          // start it, see PY-88315.
          PythonInterpreterUpdater.getInstance(project).request(entry.interpreter, PythonInterpreterUpdateRequest(packages = true, background = true))
        }
        entry.subscribed = true
      }
    }
  }

  /**
   * Returns a cached [PythonPackageManager] for the given [interpreter], creating one on first access.
   *
   * On cache hit the call is effectively free (a [ConcurrentHashMap] lookup).
   * On cache miss (once per interpreter per project lifetime) the method creates the manager
   * and registers Disposer listeners — this may involve lightweight I/O
   * (e.g. flavor detection in [com.jetbrains.python.sdk.pySdkAdditionalData]).
   * In practice the first call happens during project/SDK setup on a background thread,
   * so subsequent EDT callers always get a cached instance.
   *
   * A cached manager that no longer [PythonPackageManager.matchesSdk] is replaced. Its watcher and the manager are
   * disposed, and a new manager is built. The check runs on every call and never inside the build.
   *
   * The interpreter paths are watched only while a module of the project uses [interpreter]; see [syncWatchers].
   *
   * Requires the SDK of [interpreter] to be a Python SDK with [com.jetbrains.python.sdk.PythonSdkAdditionalData].
   */
  override fun forPythonInterpreter(project: Project, interpreter: PythonInterpreter): PythonPackageManager {
    cache[interpreter]?.takeIf { it.isCurrent() }?.let { return it.manager }

    var newEntry = false
    val entry = watchersLock.withLock {
      cache.compute(interpreter) { _, cached ->
        if (cached != null && cached.isCurrent()) return@compute cached
        newEntry = true
        if (cached != null) dropEntry(cached)
        CachedManager(interpreter, createManager(project, interpreter)).also(::removeOnSdkDisposal)
      }!!
    }
    // Only a new entry needs one: an entry that is already cached gets its watcher from the next structure that moves
    // its interpreter into use or out of it. A structure that has not landed yet arrives on the flow shortly.
    if (newEntry) syncWatchers(EvoPyProjectModel.getInstance(project).snapshotOrNull())
    return entry.manager
  }

  override fun cachedFor(interpreter: PythonInterpreter): PythonPackageManager? =
    cache[interpreter]?.takeIf { it.isCurrent() }?.manager

  /** Disposes every cached manager, so the next call builds each one again, see [forPythonInterpreter]. */
  private fun dropAllManagers() {
    watchersLock.withLock {
      for (entry in cache.values) dropEntry(entry)
      cache.clear()
    }
  }

  /** Always call under [watchersLock]. Disposes the watcher of [entry], whose parent is the SDK, and then its manager. */
  private fun dropEntry(entry: CachedManager) {
    logger.info("The manager of '${entry.sdk.name}' is not the current one, so it is rebuilt")
    entry.watcher?.let { Disposer.dispose(it) }
    entry.watcher = null
    entry.subscribed = false
    entry.removal?.let { Disposer.dispose(it) }
    entry.removal = null
    Disposer.dispose(entry.manager)
  }

  /**
   * Removes [entry] when its SDK goes. It removes only [entry], so the disposal of an old SDK keeps the entry of a newer
   * SDK of the same interpreter.
   */
  private fun removeOnSdkDisposal(entry: CachedManager) {
    val sdk = entry.sdk as? Disposable ?: return
    val localCache = cache
    val removal = Disposable { localCache.remove(entry.interpreter, entry) }
    try {
      Disposer.register(sdk, removal)
    }
    catch (e: IncorrectOperationException) {
      Disposer.dispose(entry.manager)
      throw AlreadyDisposedException("Requesting a package manager for an already disposed SDK $sdk, ${e.localizedMessage}")
    }
    entry.removal = removal
  }

  private fun createManager(project: Project, interpreter: PythonInterpreter): PythonPackageManager {
    val manager = PythonPackageManagerProvider.EP_NAME.extensionList.firstNotNullOf { it.createPackageManager(project, interpreter) }
    try {
      Disposer.register(PyPackageCoroutine.getInstance(project), manager)
    }
    catch (e: IncorrectOperationException) {
      throw AlreadyDisposedException("Requesting a package manager for an already disposed Project $project, ${e.localizedMessage}")
    }
    // I don't think it should be here
    @Suppress("DEPRECATION")
    PythonRequirementTxtSdkUtils.migrateRequirementsTxtPathFromModuleToSdk(project, interpreter.getSdkAPI())
    return manager
  }

  /** The disposable that owns the watcher of the paths of [interpreter], or `null` when they are not watched. */
  @TestOnly
  internal fun interpreterPathsWatcher(interpreter: PythonInterpreter): Disposable? = cache[interpreter]?.watcher

  /** Whether the paths of [interpreter] are watched: its watcher has its subscription. */
  @TestOnly
  internal fun watchesInterpreterPaths(interpreter: PythonInterpreter): Boolean =
    cache[interpreter]?.let { it.watcher != null && it.subscribed } == true

  /**
   * Takes the watcher from every entry before dropping them.
   *
   * Clearing the cache alone leaks the project: a watcher is parented to the interpreter, which lives as long as the
   * application, and the change it runs holds this service and through it the project (PY-89433). The reference runs
   * from the interpreter inward, so letting go of the entries does not break it — the watcher has to go.
   */
  override fun dispose() {
    watchersLock.withLock {
      for (entry in cache.values) {
        entry.watcher?.let { Disposer.dispose(it) }
        entry.watcher = null
      }
    }
    cache.clear()
  }

  private companion object {
    val logger = fileLogger()
  }
}
