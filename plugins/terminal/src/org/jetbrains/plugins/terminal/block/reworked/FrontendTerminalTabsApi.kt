// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
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
import java.util.concurrent.CompletableFuture

@Service(Service.Level.PROJECT)
internal class FrontendTerminalTabsApi(private val project: Project, private val coroutineScope: CoroutineScope) {
  fun getStoredTerminalTabs(): CompletableFuture<List<TerminalSessionTab>> {
    return coroutineScope.async {
      TerminalTabsManagerApi.getInstance().getTerminalTabs(project.projectId())
    }.asCompletableFuture()
  }

  fun createNewTerminalTab(): CompletableFuture<TerminalSessionTab> {
    return coroutineScope.async {
      TerminalTabsManagerApi.getInstance().createNewTerminalTab(project.projectId())
    }.asCompletableFuture()
  }

  fun closeTerminalTab(tabId: Int) {
    coroutineScope.launch {
      TerminalTabsManagerApi.getInstance().closeTerminalTab(project.projectId(), tabId)
    }
  }

  fun renameTerminalTab(tabId: Int, newName: String, isUserDefinedName: Boolean) {
    coroutineScope.launch {
      TerminalTabsManagerApi.getInstance().renameTerminalTab(project.projectId(), tabId, newName, isUserDefinedName)
    }
  }

  @RequiresEdt
  fun startTerminalSessionForWidget(
    widget: TerminalWidget,
    options: ShellStartupOptions,
    sessionTab: TerminalSessionTab,
    deferSessionStartUntilUiShown: Boolean,
  ) {
    val doStart = Runnable {
      doStartTerminalSessionForWidget(widget, options, sessionTab)
    }
    if (deferSessionStartUntilUiShown) {
      // Can't use coroutine `launchOnceOnShow` here because the terminal toolwindow is adding and removing component
      // from the hierarchy several times during tabs restore.
      // It cancels the coroutine, leaving the terminal session stuck not started.
      UiNotifyConnector.doWhenFirstShown(widget.component, doStart, parent = widget)
    }
    else doStart.run()
  }

  private fun doStartTerminalSessionForWidget(
    widget: TerminalWidget,
    options: ShellStartupOptions,
    sessionTab: TerminalSessionTab,
  ) {
    val job = coroutineScope.launch(Dispatchers.EDT, CoroutineStart.UNDISPATCHED) {
      doStartTerminalSession(widget, options, sessionTab)
    }
    Disposer.register(widget) {
      job.cancel()
    }
  }

  private suspend fun doStartTerminalSession(
    widget: TerminalWidget,
    options: ShellStartupOptions,
    sessionTab: TerminalSessionTab,
  ) {
    val startedSessionTab = if (sessionTab.sessionId != null) {
      // Session is already started for this tab, reuse it
      sessionTab
    }
    else {
      // Start the new terminal session
      val optionsWithSize = updateTerminalSizeFromWidget(options, widget)
      withContext(Dispatchers.IO) {
        startTerminalSessionForTab(optionsWithSize, sessionTab.id)
      }
    }

    val sessionId = startedSessionTab.sessionId
                    ?: error("Updated TerminalSessionTab does not contain sessionId after terminal session start")
    val session = FrontendTerminalSession(sessionId)
    widget.connectToSession(session)

    if (startedSessionTab.portForwardingId != null) {
      setupPortForwarding(startedSessionTab.portForwardingId, widget)
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

  private suspend fun startTerminalSessionForTab(options: ShellStartupOptions, tabId: Int): TerminalSessionTab {
    val api = TerminalTabsManagerApi.getInstance()
    return api.startTerminalSessionForTab(project.projectId(), tabId, options.toDto())
  }

  private suspend fun setupPortForwarding(id: TerminalPortForwardingId, widget: TerminalWidget) {
    val component = TerminalPortForwardingUiProvider.getInstance(project).createComponent(id, disposable = widget)
    if (component != null) {
      widget.addNotification(component, disposable = widget)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): FrontendTerminalTabsApi = project.service()
  }
}