// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.Result
import com.jetbrains.python.sdk.ObservableMutex.AlreadyLocked
import com.jetbrains.python.sdk.impl.PySdkBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

/**
 * Per-project mutex that serializes Python SDK configuration operations — both
 * auto-configuration on startup and manual SDK creation from the UI.
 *
 * This is per-project (not global) so that SDK configuration in one project
 * does not trigger spurious "Checking existing environments" notifications in others.
 *
 * Within a project the mutex is still global (not per-module) because setting an SDK on one module
 * can affect others via inherited project SDK in multi-module workspaces.
 *
 * Observe [isSdkConfigurationInProgress] to track whether an SDK configuration is running.
 */
@Service(Service.Level.PROJECT)
internal class PythonSdkConfigurationMutexService {
  val mutex: ObservableMutex = ObservableMutex()
}

/**
 * Observable state: `true` while any SDK configuration operation holds the lock.
 */
val Project.isSdkConfigurationInProgress: StateFlow<Boolean>
  @ApiStatus.Internal
  get() = service<PythonSdkConfigurationMutexService>().mutex.isLocked

/**
 * Acquires the SDK configuration mutex with appropriate progress indication.
 *
 * In a non-modal context, suspends until the lock is available.
 * In a modal context, attempts to acquire the lock without suspending and throws
 * [IllegalStateException] if it is already held (waiting would deadlock the EDT).
 *
 * Progress type is chosen automatically based on the coroutine's modality state
 * (see [withProgressRespectModality]).
 *
 * For a non-blocking variant that returns [Result.Failure] instead of throwing,
 * use [tryWithSdkConfigurationLock].
 * For `@RequiresEdt` blocking callers, use [runWithSdkConfigurationLock].
 *
 * @throws IllegalStateException if called in a modal context while the lock is already held.
 */
@ApiStatus.Internal
suspend fun <T> withSdkConfigurationLock(
  project: Project,
  action: suspend CoroutineScope.() -> T,
): T = withProgressRespectModality(project, serializeBackgroundTasks = true) {
  action()
}.getOr {
  throw IllegalStateException("this method shouldn't be called in a modal context if the lock is already held, because it leads to deadlock.")
}

/**
 * Wraps [action] in progress indication appropriate for the current modality
 * and acquires the SDK configuration mutex.
 *
 * - **Non-modal** context: uses [withBackgroundProgress].
 *   If [serializeBackgroundTasks] is `true`, suspends until the lock is available;
 *   otherwise tries to acquire without suspending.
 * - **Modal** context (e.g., inside a dialog): always uses [withModalProgress]
 *   with a non-blocking lock attempt, because suspending here would deadlock the EDT.
 *
 * @return [Result.Success] with the action's result, or [Result.Failure] with [AlreadyLocked]
 *         if the lock could not be acquired without suspending.
 */
private suspend fun <T> withProgressRespectModality(
  project: Project,
  serializeBackgroundTasks: Boolean,
  action: suspend CoroutineScope.() -> T,
): Result<T, AlreadyLocked> {
  val contextModality = currentCoroutineContext().contextModality()
  return if (contextModality == null || contextModality == ModalityState.nonModal()) {
    withBackgroundProgress(project, PySdkBundle.message("python.configuring.interpreter.progress")) {
      if (serializeBackgroundTasks) {
        project.service<PythonSdkConfigurationMutexService>().mutex.withLock {
          action()
        }.let { Result.success(it) }
      }
      else {
        project.service<PythonSdkConfigurationMutexService>().mutex.tryWithLock {
          action()
        }
      }
    }
  }
  else {
    withModalProgress(project, PySdkBundle.message("python.configuring.interpreter.progress.title")) {
      project.service<PythonSdkConfigurationMutexService>().mutex.tryWithLock {
        action()
      }
    }
  }
}

/**
 * Non-blocking variant of [withSdkConfigurationLock].
 *
 * Attempts to acquire the mutex without suspending regardless of modality.
 * Returns [Result.Failure] with [AlreadyLocked] immediately if the lock is already held;
 * otherwise runs [action] under the lock with progress indication
 * (see [withProgressRespectModality]).
 */
@ApiStatus.Internal
suspend fun <T> tryWithSdkConfigurationLock(
  project: Project,
  action: suspend CoroutineScope.() -> T,
): Result<T, AlreadyLocked> = withProgressRespectModality(project, serializeBackgroundTasks = false) {
  action()
}

/**
 * Blocking variant of [withSdkConfigurationLock] for `@RequiresEdt` callers.
 *
 * Runs [action] under the SDK configuration lock inside [runWithModalProgressBlocking],
 * which keeps the EDT responsive via a modal progress dialog.
 * Because the modal progress creates a modal context, the lock acquisition is non-blocking:
 * throws [IllegalStateException] if the lock is already held (see [withSdkConfigurationLock]).
 */
@ApiStatus.Internal
@RequiresEdt
@RequiresBlockingContext
fun <T> runWithSdkConfigurationLock(
  project: Project,
  action: suspend CoroutineScope.() -> T,
): T = runWithModalProgressBlocking(project, PySdkBundle.message("python.configuring.interpreter.progress.title")) {
  withSdkConfigurationLock(project, action)
}
