// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository.git

import com.intellij.platform.util.progress.rawProgressReporter
import kotlinx.coroutines.job
import org.eclipse.jgit.lib.ProgressMonitor
import org.jetbrains.annotations.Nls
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

suspend fun progressMonitor(): ProgressMonitor {
  return JGitCoroutineProgressMonitor(coroutineContext)
}

private class JGitCoroutineProgressMonitor(private val context: CoroutineContext) : ProgressMonitor {
  override fun start(totalTasks: Int) {
  }

  override fun beginTask(@Nls title: String, totalWork: Int) {
    context.rawProgressReporter?.details(title)
  }

  override fun update(completed: Int) {
    // todo
  }

  override fun endTask() {
    context.rawProgressReporter?.details("")
  }

  override fun isCancelled() = context.job.isCancelled
  override fun showDuration(enabled: Boolean) {
    // todo
  }
}