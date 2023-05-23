// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.withBackgroundProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * [runBlockingCancellable] requires a progress indicator.
 * This method creates an indicator and calls [runBlockingCancellable].
 * Otherwise [runBlockingCancellable] would throw [IllegalStateException].
 */
@RequiresBackgroundThread
@RequiresBlockingContext
fun <T> runBlockingCancellableUnderIndicator(action: suspend CoroutineScope.() -> T): T {
  val process: () -> T = {
    runBlockingCancellable {
      return@runBlockingCancellable action()
    }
  }
  return ProgressManager.getInstance().runProcess(process, EmptyProgressIndicator())
}

/**
 * If [MavenUtil.isNoBackgroundMode] is true, call the action directly.
 * If it is false, call the action inside [withBackgroundProgress].
 * Calling [withBackgroundProgress] in "no background mode" leads to deadlocks.
 * "No background mode" should eventually be eliminated, and then all
 * [withBackgroundProgressIfApplicable] usages should be replaced with [withBackgroundProgress].
 */
suspend fun <T> withBackgroundProgressIfApplicable(
  project: Project,
  title: @NlsContexts.ProgressTitle String,
  cancellable: Boolean,
  action: suspend CoroutineScope.() -> T
): T {
  if (MavenUtil.isNoBackgroundMode()) return action(CoroutineScope(SupervisorJob()))
  return withBackgroundProgress(project, title, cancellable, action)
}