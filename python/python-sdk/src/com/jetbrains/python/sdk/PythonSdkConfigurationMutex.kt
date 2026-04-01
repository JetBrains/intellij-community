// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.Result
import com.jetbrains.python.sdk.ObservableMutex.AlreadyLocked
import com.jetbrains.python.sdk.impl.PySdkBundle
import kotlinx.coroutines.CoroutineScope
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
 * Acquires the SDK configuration mutex inside [withModalProgress].
 *
 * The modal progress wrapper is essential to prevent deadlocks. SDK configuration
 * code typically needs the EDT — for example, `SdkConfigurationUtil.setupSdk` calls
 * `invokeAndWait`, and other paths use `edtWriteAction` or `withContext(Dispatchers.EDT)`.
 *
 * Without modal progress the following deadlock occurs:
 * 1. A background coroutine (launched with `Dispatchers.Default`, no modality state)
 *    acquires the mutex and enters SDK configuration code.
 * 2. That code posts an EDT dispatch at `NON_MODAL` level
 *    (the default when no modality is in the coroutine context).
 * 3. Meanwhile EDT is blocked — either pumping a modal dialog's event loop
 *    (which only processes events at that modal level or higher) or waiting
 *    for a write lock.
 * 4. The `NON_MODAL` dispatch never executes → the background coroutine never
 *    completes → the mutex is never released → deadlock.
 *
 * [withModalProgress] solves this by:
 * - Installing a proper modality state in the coroutine context,
 *   so all EDT dispatches inside [action] are posted at the modal level.
 * - Showing a progress dialog that keeps the EDT responsive at that level.
 *
 * Use this for background coroutines. For `@RequiresEdt` blocking callers,
 * use [runWithSdkConfigurationLock].
 */
@ApiStatus.Internal
suspend fun <T> withSdkConfigurationLock(
  project: Project,
  action: suspend CoroutineScope.() -> T,
): T = withModalProgress(project, PySdkBundle.message("python.configuring.interpreter.progress.title")) {
  project.service<PythonSdkConfigurationMutexService>().mutex.withLock {
    action()
  }
}

/**
 * Non-blocking variant of [withSdkConfigurationLock].
 *
 * Attempts to acquire the mutex without suspending.
 * Returns [Result.Failure] with [AlreadyLocked] immediately if the lock is held;
 * otherwise runs [action] inside [withModalProgress] (see [withSdkConfigurationLock] for rationale).
 */
@ApiStatus.Internal
suspend fun <T> tryWithSdkConfigurationLock(
  project: Project,
  action: suspend CoroutineScope.() -> T,
): Result<T, AlreadyLocked> = withModalProgress(project, PySdkBundle.message("python.configuring.interpreter.progress.title")) {
  project.service<PythonSdkConfigurationMutexService>().mutex.tryWithLock {
    action()
  }
}

/**
 * Blocking variant of [withSdkConfigurationLock] for `@RequiresEdt` callers.
 *
 * Combines `runWithModalProgressBlocking` with the SDK configuration lock in a single call.
 *
 * See [withSdkConfigurationLock] for the deadlock rationale.
 */
@ApiStatus.Internal
@RequiresEdt
@RequiresBlockingContext
fun <T> runWithSdkConfigurationLock(
  project: Project,
  action: suspend CoroutineScope.() -> T,
): T = runWithModalProgressBlocking(project, PySdkBundle.message("python.configuring.interpreter.progress.title")) {
  project.service<PythonSdkConfigurationMutexService>().mutex.withLock {
    action()
  }
}
