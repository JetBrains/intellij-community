// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.ide.ActivityTracker
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val MUTEX_KEY = Key.create<Mutex>("${PythonPackageManagerJobService::class.java.name}.mutex")

/**
 * This service allows asynchronous and potentially long-running tasks tied to a specific [PythonPackageManager] instance (per SDK)
 * to be executed safely, ensuring that only one job for a given manager is active at a time.
 * Managers are command line tools, and they can't run commands in parallel.
 *
 * The service is thread-safe.
 *
 * **Note**: [PythonPackageManager] instances are considered equal if they refer to the same SDK.
 * There is no equals/hashcode override for [PythonPackageManager] instances,
 * so they are compared by reference only and we rely on [PythonPackageManagerServiceImpl.forSdk] / [PythonPackageManagerServiceImpl.cache]
 *
 * It also provides activity tracking signals to ensure proper UI updates.
 */
@Service(Service.Level.PROJECT)
internal class PythonPackageManagerJobService {

  private fun getMutex(manager: PythonPackageManager): Mutex {
    return synchronized(manager.data) {
      manager.data.getOrCreateUserData(MUTEX_KEY) { Mutex() }
    }
  }

  fun isRunLocked(manager: PythonPackageManager): Boolean = getMutex(manager).isLocked

  /**
   * Runs a [runnable] synchronized for a given [PythonPackageManager] instance.
   * The method ensures that only one runnable per manager can be active at a time.
   * If a job is already active for the given manager, it locks on the manager's mutex.
   * Upon completion of the job, it triggers update cycles for activity tracking.
   *
   * @param [runnable] a suspendable function that represents the operation to be performed, producing a [Result] with a success type [V] or a failure type [PyError].
   */
  suspend fun <V> runSynchronized(
    manager: PythonPackageManager,
    title: @ProgressTitle String,
    runnable: suspend () -> Result<V, PyError>,
  ): Result<V, PyError> {

    val mutex = getMutex(manager)

    return withBackgroundProgress(manager.project, title, cancellable = true) {
      mutex.withLock {
        runnable.invoke()
      }.also {
        ActivityTracker.getInstance().inc() // it forces the next update cycle to give all waiting/currently disabled actions a callback
      }
    }
  }

  companion object {
    fun getInstance(project: Project): PythonPackageManagerJobService = project.service()
  }
}

internal fun PythonPackageManager.isRunLocked(): Boolean {
  return PythonPackageManagerJobService.getInstance(this.project).isRunLocked(this)
}

internal suspend fun <V> PythonPackageManager.runSynchronized(
  title: @ProgressTitle String,
  runnable: suspend () -> Result<V, PyError>,
): Result<V, PyError> {
  val jobService = PythonPackageManagerJobService.getInstance(this.project)
  return jobService.runSynchronized(this, title, runnable)
}
