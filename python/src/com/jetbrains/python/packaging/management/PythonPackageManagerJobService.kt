// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.ide.ActivityTracker
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.sdk.PythonSdkCoroutineService
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.coroutines.CoroutineContext

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
  private val deferredJobs: ConcurrentMap<PythonPackageManager, Deferred<Result<*, PyError>>> = ConcurrentHashMap()

  fun getLastExecutedJob(manager: PythonPackageManager): Deferred<Result<*, PyError>>? {
    return deferredJobs[manager]
  }

  /**
   * Runs a deferred job asynchronously for a given [PythonPackageManager] instance. The method ensures that only one
   * job per manager can be active at a time. If a job is already active for the given manager, it returns null.
   * Upon completion of the job, it performs a necessary cleanup and triggers update cycles for activity tracking.
   *
   * @param [scope] the `CoroutineScope` to be used for launching the deferred job. Default is the scope from [PythonSdkCoroutineService.cs].
   * @param [context] the `CoroutineContext` in which the coroutine is executed. Default is [Dispatchers.IO].
   * @param [runnable] a suspendable function that represents the operation to be performed, producing a [Result] with a success type [V] or a failure type [PyError].
   * @return a [Deferred] representing the asynchronous computation of the job, or null if a job is already active for the given manager.
   */
  fun <V> tryRunDeferredJob(
    manager: PythonPackageManager,
    scope: CoroutineScope = service<PythonSdkCoroutineService>().cs,
    context: CoroutineContext = Dispatchers.IO,
    runnable: suspend () -> Result<V, PyError>,
  ): Deferred<Result<V, PyError>>? {

    val job = synchronized(deferredJobs) {
      if (deferredJobs[manager]?.isCompleted == false) return@synchronized null

      scope.async(context) { runnable.invoke() }.also {
        deferredJobs[manager] = it
      }
    }

    job?.invokeOnCompletion { exception ->
      synchronized(deferredJobs) {
        deferredJobs.remove(manager, job)  // this cleanup line might be removed if someone needs the last deferred job result in other places
      }
      ActivityTracker.getInstance().inc() // it forces the next update cycle to give all waiting/currently disabled actions a callback
    }

    return job
  }

  companion object {
    fun getInstance(project: Project?): PythonPackageManagerJobService? = project?.service()
  }
}

internal fun PythonPackageManager.getLastExecutedJob(): Deferred<Result<*, PyError>>? {
  return PythonPackageManagerJobService.getInstance(this.project)?.getLastExecutedJob(this)
}

internal fun <V> PythonPackageManager.tryRunDeferredJob(
  scope: CoroutineScope = service<PythonSdkCoroutineService>().cs,
  dispatcher: CoroutineDispatcher = Dispatchers.IO,
  runnable: suspend () -> Result<V, PyError>,
): Deferred<Result<V, PyError>>? {
  val jobService = PythonPackageManagerJobService.getInstance(this.project) ?: return null
  return jobService.tryRunDeferredJob(this, scope, dispatcher, runnable)
}
