package org.jetbrains.settingsRepository.git

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import org.eclipse.jgit.lib.NullProgressMonitor
import org.eclipse.jgit.lib.ProgressMonitor

fun ProgressIndicator?.asProgressMonitor() = if (this == null || this is EmptyProgressIndicator) NullProgressMonitor.INSTANCE else JGitProgressMonitor(this)

private class JGitProgressMonitor(private val indicator: ProgressIndicator) : ProgressMonitor {
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