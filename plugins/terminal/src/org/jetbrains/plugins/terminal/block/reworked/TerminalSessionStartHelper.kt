// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.project.projectId
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.block.reworked.session.FrontendTerminalSession
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalSessionTab
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalPortForwardingId
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalTabsManagerApi
import org.jetbrains.plugins.terminal.block.reworked.session.toDto
import org.jetbrains.plugins.terminal.util.terminalProjectScope
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
    val job = terminalProjectScope(project).launch(Dispatchers.EDT, CoroutineStart.UNDISPATCHED) {
      doStartTerminalSession(project, widget, options, sessionTab)
    }
    Disposer.register(widget) {
      job.cancel()
    }
  }

  private suspend fun doStartTerminalSession(
    project: Project,
    widget: TerminalWidget,
    options: ShellStartupOptions,
    sessionTab: TerminalSessionTab,
  ) {
    val startedSessionTab = if (sessionTab.sessionId != null) {
      // Session is already started for this tab, reuse it
      sessionTab
    }
    else {
      // Start new terminal session
      val optionsWithSize = updateTerminalSizeFromWidget(options, widget)
      withContext(Dispatchers.IO) {
        startTerminalSessionForTab(project, optionsWithSize, sessionTab.id)
      }
    }

    val sessionId = startedSessionTab.sessionId
                    ?: error("Updated TerminalSessionTab does not contain sessionId after terminal session start")
    val session = FrontendTerminalSession(sessionId)
    widget.connectToSession(session)

    if (startedSessionTab.portForwardingId != null) {
      setupPortForwarding(project, startedSessionTab.portForwardingId, widget)
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

  private suspend fun startTerminalSessionForTab(project: Project, options: ShellStartupOptions, tabId: Int): TerminalSessionTab {
    val api = TerminalTabsManagerApi.getInstance()
    return api.startTerminalSessionForTab(project.projectId(), tabId, options.toDto())
  }

  private suspend fun setupPortForwarding(project: Project, id: TerminalPortForwardingId, widget: TerminalWidget) {
    val component = TerminalPortForwardingUiProvider.getInstance(project).createComponent(id, disposable = widget)
    if (component != null) {
      widget.addNotification(component, disposable = widget)
    }
  }
}