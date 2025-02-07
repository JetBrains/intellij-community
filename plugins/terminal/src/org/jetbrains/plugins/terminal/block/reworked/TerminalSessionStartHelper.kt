// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.terminal.session.TerminalCloseEvent
import com.intellij.terminal.session.TerminalSession
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
  @RequiresEdt
  fun startTerminalSessionForWidget(
    project: Project,
    widget: TerminalWidget,
    options: ShellStartupOptions,
    tabId: Int?,
    deferSessionStartUntilUiShown: Boolean,
  ) {
    val doStart = Runnable {
      doStartTerminalSessionForWidget(project, widget, options, tabId)
    }
    if (deferSessionStartUntilUiShown) {
      // Can't use coroutine `launchOnceOnShow` here because terminal toolwindow is adding and removing component
      // from the hierarchy several times during tabs restore.
      // It cancels the coroutine, leaving the terminal session stuck not started.
      UiNotifyConnector.doWhenFirstShown(widget.component, doStart, parent = widget)
    }
    else doStart.run()
  }

  @JvmStatic
  fun closeTerminalSession(project: Project, session: TerminalSession) {
    terminalProjectScope(project).launch {
      session.sendInputEvent(TerminalCloseEvent)
    }
  }

  private fun doStartTerminalSessionForWidget(
    project: Project,
    widget: TerminalWidget,
    options: ShellStartupOptions,
    tabId: Int?,
  ) {
    terminalProjectScope(project).launch(Dispatchers.EDT, CoroutineStart.UNDISPATCHED) {
      val initialSizeFuture = widget.getTerminalSizeInitializedFuture()
      val initialSize = initialSizeFuture.await()
      val optionsWithSize = if (initialSize != null) {
        options.builder().initialTermSize(initialSize).build()
      }
      else options

      val session = withContext(Dispatchers.IO) {
        startTerminalSession(project, optionsWithSize, tabId)
      }

      widget.connectToSession(session)
    }
  }

  private suspend fun startTerminalSession(project: Project, options: ShellStartupOptions, tabId: Int?): TerminalSession {
    val api = TerminalTabsManagerApi.getInstance()

    val sessionId = if (tabId != null) {
      // TerminalSessionTab is already existing, start session for it
      api.startTerminalSessionForTab(project.projectId(), tabId, options.toDto())
    }
    else {
      // Create new TerminalSessionTab
      val tab = api.createNewTerminalTab(project.projectId(), options.toDto())
      tab.sessionId ?: error("Session ID is absent in the newly created TerminalSessionTab: $tab")
    }

    return FrontendTerminalSession(sessionId)
  }
}