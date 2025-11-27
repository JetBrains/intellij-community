// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.execution.TerminateRemoteProcessDialog
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.ui.BaseContentCloseListener
import com.intellij.execution.ui.RunContentManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import kotlin.time.TimeSource

@ApiStatus.Internal
abstract class TerminalTabCloseListener(
  private val content: Content,
  private val project: Project,
  parentDisposable: Disposable,
) : BaseContentCloseListener(content, project, parentDisposable) {
  override fun disposeContent(content: Content) {
  }

  override fun closeQuery(content: Content, projectClosing: Boolean): Boolean {
    if (projectClosing) {
      return true
    }
    if (content.getUserData(Content.TEMPORARY_REMOVED_KEY) == true) {
      return true
    }

    val startTime = TimeSource.Monotonic.markNow()
    try {
      if (!hasChildProcesses(content)) {
        return true
      }
    }
    catch (e: Exception) {
      LOG.error(e)
    }
    finally {
      val checkDuration = startTime.elapsedNow()
      ReworkedTerminalUsageCollector.logTabClosingCheckLatency(checkDuration)
    }

    val proxy = NopProcessHandler().apply { startNotify() }
    // don't show 'disconnect' button
    proxy.putUserData(RunContentManagerImpl.ALWAYS_USE_DEFAULT_STOPPING_BEHAVIOUR_KEY, true)
    val result = TerminateRemoteProcessDialog.show(project, "Terminal ${content.displayName}", proxy)
    return result != null
  }

  abstract fun hasChildProcesses(content: Content): Boolean

  override fun canClose(project: Project): Boolean {
    return project === this.project && closeQuery(this.content, true)
  }

  companion object {
    /**
     * If you remove the content from the tool window content manager using this method,
     * close the tool window manually in case it became empty.
     * Because it won't be closed by the platform logic because of [Content.TEMPORARY_REMOVED_KEY] we set.
     */
    fun executeContentOperationSilently(content: Content, runnable: () -> Unit) {
      content.putUserData(Content.TEMPORARY_REMOVED_KEY, true)
      try {
        runnable()
      }
      finally {
        content.putUserData(Content.TEMPORARY_REMOVED_KEY, null)
      }
    }
  }
}

private val LOG = logger<TerminalTabCloseListener>()
