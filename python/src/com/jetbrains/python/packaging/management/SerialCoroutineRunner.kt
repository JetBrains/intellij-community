// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.ide.ActivityTracker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.getOrCreateUserDataUnsafe
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val MUTEX_KEY = Key.create<Mutex>("${SerialCoroutineRunner::class.java.name}.mutex")

/**
 * This service allows asynchronous and potentially long-running tasks tied to a specific [UserDataHolder] instance
 * to be executed safely, ensuring that only one job for a given object is active at a time.
 *
 * The service is thread-safe.
 *
 * It also provides activity tracking signals to ensure proper UI updates.
 */
private object SerialCoroutineRunner {

  private fun getMutex(userDataHolder: UserDataHolder): Mutex {
    return synchronized(userDataHolder) {
      userDataHolder.getOrCreateUserDataUnsafe(MUTEX_KEY) { Mutex() }
    }
  }

  fun isRunLocked(userDataHolder: UserDataHolder): Boolean = getMutex(userDataHolder).isLocked

  /**
   * Runs a [runnable] synchronized for a given [UserDataHolder] instance.
   * The method ensures that only one runnable per manager can be active at a time.
   * If a job is already active for the given object, it locks on the object's mutex.
   * Upon completion of the job, it triggers update cycles for activity tracking.
   *
   * @param [runnable] a suspendable function that represents the operation to be performed,
   * producing a [PyResult] with a success type [V] or a failure type [PyError].
   */
  suspend fun <V> run(
    project: Project,
    userDataHolder: UserDataHolder,
    title: @ProgressTitle String,
    runnable: suspend () -> PyResult<V>,
  ): PyResult<V> {

    val mutex = getMutex(userDataHolder)

    return withBackgroundProgress(project, title, cancellable = true) {
      mutex.withLock {
        runnable.invoke()
      }.also {
        ActivityTracker.getInstance().inc() // it forces the next update cycle to give all waiting/currently disabled actions a callback
      }
    }
  }
}

internal fun PythonPackageManager.isRunLocked(): Boolean {
  return SerialCoroutineRunner.isRunLocked(this.sdk)
}

internal suspend fun <V> PythonPackageManager.runSynchronized(
  title: @ProgressTitle String,
  runnable: suspend () -> PyResult<V>,
): PyResult<V> {
  return SerialCoroutineRunner.run(this.project, this.sdk, title, runnable)
}
