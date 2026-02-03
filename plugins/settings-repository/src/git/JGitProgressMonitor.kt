// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository.git

import com.intellij.platform.util.progress.RawProgressReporter
import kotlinx.coroutines.Job
import org.eclipse.jgit.lib.ProgressMonitor
import org.jetbrains.annotations.Nls

internal class JGitCoroutineProgressMonitor(
  private val job: Job,
  private val reporter: RawProgressReporter?,
) : ProgressMonitor {

  override fun start(totalTasks: Int) {
  }

  override fun beginTask(@Nls title: String, totalWork: Int) {
    reporter?.details(title)
  }

  override fun update(completed: Int) {
    // todo
  }

  override fun endTask() {
    reporter?.details("")
  }

  override fun isCancelled() = job.isCancelled
  override fun showDuration(enabled: Boolean) {
    // todo
  }
}