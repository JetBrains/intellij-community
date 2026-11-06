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

private val MUTEX_KEY = Key.create<Mutex>("${CancellableJobSerialRunner::class.java.name}.mutex")

/**
 * This service allows asynchronous and potentially long-running jobs tied to a specific [UserDataHolder] instance
 * to be executed safely, ensuring that only one job for a given object is active at a time.
 *
 * The service is thread-safe.
 *
 * It also provides activity tracking signals to ensure proper UI updates.
 */
internal object CancellableJobSerialRunner {

  private fun getMutex(holder: UserDataHolder): Mutex {
    return synchronized(holder) {
      holder.getOrCreateUserDataUnsafe(MUTEX_KEY) { Mutex() }
    }
  }

  fun isRunLocked(holder: UserDataHolder): Boolean = getMutex(holder).isLocked

  /**
   * Runs a [runnable] job synchronized on a given [UserDataHolder] instance.
   * The method ensures that only one runnable per holder can be active at a time.
   * It always creates cancellable background progress for each job so the job queue might be managed from the UI.
   * If an active job already exists for the given holder, the coroutine suspends until it can acquire the holder's mutex.
   * Upon completion of the job, it triggers activity tracking to refresh UI components visibility.
   *
   * @param [runnable] a suspendable function that represents the job to be performed,
   * producing a [PyResult] with a success type [V] or a failure type [PyError].
   */
  suspend fun <V> run(
    project: Project,
    holder: UserDataHolder,
    title: @ProgressTitle String,
    runnable: suspend () -> PyResult<V>,
  ): PyResult<V> {

    val mutex = getMutex(holder)

    return withBackgroundProgress(project, title, cancellable = true) {
      mutex.withLock {
        runnable.invoke()
      }.also {
        ActivityTracker.getInstance().inc() // it forces the next update cycle to give all waiting/currently disabled actions a callback
      }
    }
  }
}
