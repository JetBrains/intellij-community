// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository.git

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import org.eclipse.jgit.lib.NullProgressMonitor
import org.eclipse.jgit.lib.ProgressMonitor
import org.jetbrains.annotations.Nls

fun ProgressIndicator?.asProgressMonitor(): ProgressMonitor = if (this == null || this is EmptyProgressIndicator) NullProgressMonitor.INSTANCE else JGitProgressMonitor(this)

private class JGitProgressMonitor(private val indicator: ProgressIndicator) : ProgressMonitor {
  override fun start(totalTasks: Int) {
  }

  override fun beginTask(@Nls title: String, totalWork: Int) {
    indicator.text2 = title
  }

  override fun update(completed: Int) {
    // todo
  }

  override fun endTask() {
    indicator.text2 = ""
  }

  override fun isCancelled() = indicator.isCanceled
}