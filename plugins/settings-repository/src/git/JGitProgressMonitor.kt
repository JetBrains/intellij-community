package org.jetbrains.settingsRepository.git

import com.intellij.openapi.progress.ProgressIndicator
import org.eclipse.jgit.lib.ProgressMonitor

class JGitProgressMonitor(private val indicator: ProgressIndicator) : ProgressMonitor {
  override fun start(totalTasks: Int) {
  }

  override fun beginTask(title: String, totalWork: Int) {
    indicator.setText2(title)
  }

  override fun update(completed: Int) {
    // todo
  }

  override fun endTask() {
    indicator.setText2("")
  }

  override fun isCancelled() = indicator.isCanceled()
}