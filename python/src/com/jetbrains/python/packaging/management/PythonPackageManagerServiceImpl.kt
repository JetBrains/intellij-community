// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.IncorrectOperationException
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.bridge.PythonPackageManagementServiceBridge
import com.jetbrains.python.packaging.requirementsTxt.PythonRequirementTxtSdkUtils
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkUpdater
import com.jetbrains.python.sdk.pySdkAdditionalData
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly

internal class PythonPackageManagerServiceImpl(
  private val project: Project,
  scope: CoroutineScope,
) : PythonPackageManagerService, Disposable {
  private val cache = ConcurrentHashMap<UUID, CachedManager>()

  private val bridgeCache = ConcurrentHashMap<UUID, PythonPackageManagementServiceBridge>()

  /** Guards the watcher of every entry, because a root change and a first call can reconcile at the same moment. */
  private val watchersLock = ReentrantLock()

  init {
    // The structure of the project, and not the workspace model it is derived from: a model listener reports the
    // change earlier, while `EvoPyProjectModel` still holds the generation before it, and reading that one would
    // reconcile against the interpreters of a moment that has passed.
    scope.launch {
      EvoPyProjectModel.getInstance(project).snapshotFlow().collect { syncWatchers(it) }
    }
  }

  /** One cached manager, and the watcher of the interpreter paths that it has while the project uses the interpreter. */
  private class CachedManager(val interpreter: PythonInterpreter, val manager: PythonPackageManager) {
    var watcher: Disposable? = null

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
   * call on every read of [PythonPackageManagementServiceBridge.manager], which is a property.
   */
  private fun syncWatchers(structure: EvoPyProjectModel.Snapshot?) {
    if (cache.isEmpty() || structure == null) return
    val interpretersInUse = structure.interpreters
    watchersLock.withLock {
      for ((key, entry) in cache) {
        val watched = entry.watcher != null
        val inUse = entry.interpreter in interpretersInUse
        if (inUse && !watched) {
          watchInterpreterPaths(key, entry)
        }
        else if (!inUse && watched) {
          logger.info("The project does not use '${entry.sdk.name}' any more, so its paths are not watched")
          entry.watcher?.let { Disposer.dispose(it) }
          entry.watcher = null
        }
      }
    }
  }

  /** Always call under [watchersLock]. */
  private fun watchInterpreterPaths(key: UUID, entry: CachedManager) {
    val watcher = Disposer.newDisposable("VFS listener for ${entry.sdk.name} in scope of ${project.name}")
    try {
      // Under the interpreter, because `runOnChangeUnderInterpreterPaths` requires a parent that does not outlive it:
      // its listener throws for a disposed interpreter, on every file event of the whole application. The manager
      // outlives the interpreter, so it cannot be that parent.
      Disposer.register(entry.sdk as? Disposable ?: entry.manager, watcher)
    }
    catch (e: IncorrectOperationException) {
      logger.info("The interpreter '${entry.sdk.name}' is gone, dropping its manager", e)
      cache.remove(key, entry)
      return
    }
    PyPackageUtil.runOnChangeUnderInterpreterPaths(entry.sdk, watcher) {
      // A background request: a change under the paths of an interpreter that the project does not use must not
      // start it, see PY-88315.
      PythonSdkUpdater.scheduleBackgroundUpdate(entry.sdk, project)
    }
    entry.watcher = watcher
  }

  /**
   * Returns a cached [PythonPackageManager] for the given [interpreter], creating one on first access.
   *
   * On cache hit the call is effectively free (a [ConcurrentHashMap] lookup).
   * On cache miss (once per SDK per project lifetime) the method creates the manager
   * and registers Disposer listeners — this may involve lightweight I/O
   * (e.g. flavor detection in [com.jetbrains.python.sdk.pySdkAdditionalData]).
   * In practice the first call happens during project/SDK setup on a background thread,
   * so subsequent EDT callers always get a cached instance.
   *
   * The interpreter paths are watched only while a module of the project uses [interpreter]; see [syncWatchers].
   *
   * Requires the SDK of [interpreter] to be a Python SDK with [com.jetbrains.python.sdk.PythonSdkAdditionalData].
   */
  override fun forPythonInterpreter(project: Project, interpreter: PythonInterpreter): PythonPackageManager {
    // The managers are keyed by the SDK and its additional data, so this service reads the SDK.
    @Suppress("DEPRECATION")
    val sdk = interpreter.getSdkAPI()
    val cacheKey = (sdk.pySdkAdditionalData).uuid

    var newEntry = false
    val entry = cache.computeIfAbsent(cacheKey) {
      newEntry = true
      if (sdk is Disposable) {
        val localCache = cache
        try {
          Disposer.register(sdk, Disposable { localCache.remove(cacheKey) })
        }
        catch (e: IncorrectOperationException) {
          throw AlreadyDisposedException("Requesting a package manager for an already disposed SDK $sdk, ${e.localizedMessage}")
        }
      }

      val manager = PythonPackageManagerProvider.EP_NAME.extensionList.firstNotNullOf { it.createPackageManager(project, interpreter) }
      try {
        Disposer.register(PyPackageCoroutine.getInstance(project), manager)
      }
      catch (e: IncorrectOperationException) {
          throw AlreadyDisposedException("Requesting a package manager for an already disposed Project $project, ${e.localizedMessage}")
      }

      // I don't think it should be here
      PythonRequirementTxtSdkUtils.migrateRequirementsTxtPathFromModuleToSdk(project, sdk)

      CachedManager(interpreter, manager)
    }
    // Only a new entry needs one: an entry that is already cached gets its watcher from the next structure that moves
    // its interpreter into use or out of it. A structure that has not landed yet arrives on the flow shortly.
    if (newEntry) syncWatchers(EvoPyProjectModel.getInstance(project).snapshotOrNull())
    return entry.manager
  }

  /** The disposable that owns the watcher of the paths of [sdk], or `null` when they are not watched. */
  @TestOnly
  internal fun interpreterPathsWatcher(sdk: Sdk): Disposable? =
    cache[(sdk.sdkAdditionalData as? PythonSdkAdditionalData)?.uuid]?.watcher

  /** Whether the paths of [sdk] are watched. */
  @TestOnly
  internal fun watchesInterpreterPaths(sdk: Sdk): Boolean = interpreterPathsWatcher(sdk) != null

  override fun bridgeForSdk(project: Project, sdk: Sdk): PythonPackageManagementServiceBridge {
    val cacheKey = (sdk.sdkAdditionalData as PythonSdkAdditionalData).uuid
    return bridgeCache.computeIfAbsent(cacheKey) {
      val bridge = PythonPackageManagementServiceBridge(project, sdk)
      Disposer.register(this@PythonPackageManagerServiceImpl, bridge)
      bridge
    }
  }

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
