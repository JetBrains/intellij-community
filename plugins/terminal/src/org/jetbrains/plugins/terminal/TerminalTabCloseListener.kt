// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.execution.TerminateRemoteProcessDialog
import com.intellij.execution.TerminateRemoteProcessDialog.ProcessCloseConfirmationResult
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.ui.BaseContentCloseListener
import com.intellij.execution.ui.RunContentManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.content.Content
import kotlinx.coroutines.CancellationException
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
    if (projectClosing || project.isDisposed || ApplicationManager.getApplication().isExitInProgress) {
      return true
    }
    if (content.getUserData(Content.TEMPORARY_REMOVED_KEY) == true) {
      return true
    }

    val startTime = TimeSource.Monotonic.markNow()
    try {
      val result = shouldConfirmClosing(content)
      when (result) {
        CloseCheckResult.SHOULD_ASK_CONFIRMATION -> {
          /** proceed to show the confirmation dialog below */
        }
        CloseCheckResult.CAN_CLOSE_SILENTLY -> return true
        // Interpret explicit user cancellation of the check as "Do not close the tab".
        CloseCheckResult.CHECK_WAS_CANCELLED -> return false
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
    return result != ProcessCloseConfirmationResult.LEAVE_RUNNING
  }

  abstract fun shouldConfirmClosing(content: Content): CloseCheckResult

  override fun canClose(project: Project): Boolean {
    return project === this.project && closeQuery(this.content, true)
  }

  protected fun runCloseCheckBlocking(shouldConfirmClosing: suspend () -> Boolean): CloseCheckResult {
    return try {
      runWithModalProgressBlocking(myProject, "") {
        if (shouldConfirmClosing()) {
          CloseCheckResult.SHOULD_ASK_CONFIRMATION
        }
        else CloseCheckResult.CAN_CLOSE_SILENTLY
      }
    }
    catch (_: CancellationException) {
      ProgressManager.checkCanceled()
      // User pressed "Cancel" in the modal progress dialog.
      CloseCheckResult.CHECK_WAS_CANCELLED
    }
  }

  enum class CloseCheckResult {
    SHOULD_ASK_CONFIRMATION,
    CAN_CLOSE_SILENTLY,
    CHECK_WAS_CANCELLED,
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
