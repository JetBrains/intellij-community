// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.block.reworked.session.FrontendTerminalSession
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalSessionTab
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalTabsManagerApi
import org.jetbrains.plugins.terminal.block.reworked.session.toDto
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import java.lang.Runnable
import java.util.concurrent.CompletableFuture

internal object TerminalSessionStartHelper {
  @JvmStatic
  fun getStoredTerminalTabs(project: Project): CompletableFuture<List<TerminalSessionTab>> {
    return terminalProjectScope(project).async {
      TerminalTabsManagerApi.getInstance().getTerminalTabs(project.projectId())
    }.asCompletableFuture()
  }

  @JvmStatic
  fun createNewTerminalTab(project: Project): CompletableFuture<TerminalSessionTab> {
    return terminalProjectScope(project).async {
      TerminalTabsManagerApi.getInstance().createNewTerminalTab(project.projectId())
    }.asCompletableFuture()
  }

  @JvmStatic
  fun closeTerminalTab(project: Project, tabId: Int) {
    terminalProjectScope(project).launch {
      TerminalTabsManagerApi.getInstance().closeTerminalTab(project.projectId(), tabId)
    }
  }

  @JvmStatic
  fun renameTerminalTab(project: Project, tabId: Int, newName: String, isUserDefinedName: Boolean) {
    terminalProjectScope(project).launch {
      TerminalTabsManagerApi.getInstance().renameTerminalTab(project.projectId(), tabId, newName, isUserDefinedName)
    }
  }

  @JvmStatic
  @RequiresEdt
  fun startTerminalSessionForWidget(
    project: Project,
    widget: TerminalWidget,
    options: ShellStartupOptions,
    sessionTab: TerminalSessionTab,
    deferSessionStartUntilUiShown: Boolean,
  ) {
    val doStart = Runnable {
      doStartTerminalSessionForWidget(project, widget, options, sessionTab)
    }
    if (deferSessionStartUntilUiShown) {
      // Can't use coroutine `launchOnceOnShow` here because terminal toolwindow is adding and removing component
      // from the hierarchy several times during tabs restore.
      // It cancels the coroutine, leaving the terminal session stuck not started.
      UiNotifyConnector.doWhenFirstShown(widget.component, doStart, parent = widget)
    }
    else doStart.run()
  }

  private fun doStartTerminalSessionForWidget(
    project: Project,
    widget: TerminalWidget,
    options: ShellStartupOptions,
    sessionTab: TerminalSessionTab,
  ) {
    terminalProjectScope(project).launch(Dispatchers.EDT, CoroutineStart.UNDISPATCHED) {
      val sessionId = if (sessionTab.sessionId != null) {
        // Session is already started for this tab, reuse it
        sessionTab.sessionId
      }
      else {
        // Start new terminal session
        val optionsWithSize = updateTerminalSizeFromWidget(options, widget)
        withContext(Dispatchers.IO) {
          startTerminalSessionForTab(project, optionsWithSize, sessionTab.id)
        }
      }

      val session = FrontendTerminalSession(sessionId)
      widget.connectToSession(session)
    }
  }

  private suspend fun updateTerminalSizeFromWidget(options: ShellStartupOptions, widget: TerminalWidget): ShellStartupOptions {
    val initialSizeFuture = widget.getTerminalSizeInitializedFuture()
    val initialSize = initialSizeFuture.await()
    return if (initialSize != null) {
      options.builder().initialTermSize(initialSize).build()
    }
    else options
  }

  private suspend fun startTerminalSessionForTab(project: Project, options: ShellStartupOptions, tabId: Int): TerminalSessionId {
    val api = TerminalTabsManagerApi.getInstance()
    return api.startTerminalSessionForTab(project.projectId(), tabId, options.toDto())
  }
}