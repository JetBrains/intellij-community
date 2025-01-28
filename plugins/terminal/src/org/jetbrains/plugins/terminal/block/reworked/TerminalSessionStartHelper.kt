// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.terminal.session.TerminalSession
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.launchOnceOnShow
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.block.reworked.session.FrontendTerminalSession
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionsManagerApi
import org.jetbrains.plugins.terminal.block.reworked.session.toDto
import org.jetbrains.plugins.terminal.util.terminalProjectScope

internal object TerminalSessionStartHelper {
  @JvmStatic
  @RequiresEdt
  fun startTerminalSessionForWidget(
    project: Project,
    widget: TerminalWidget,
    options: ShellStartupOptions,
    deferSessionStartUntilUiShown: Boolean,
  ) {
    if (deferSessionStartUntilUiShown) {
      widget.component.launchOnceOnShow("Terminal session start") {
        doStartTerminalSessionForWidget(project, widget, options)
      }
    }
    else doStartTerminalSessionForWidget(project, widget, options)
  }

  private fun doStartTerminalSessionForWidget(
    project: Project,
    widget: TerminalWidget,
    options: ShellStartupOptions,
  ) {
    terminalProjectScope(project).launch(Dispatchers.EDT, CoroutineStart.UNDISPATCHED) {
      val initialSizeFuture = widget.getTerminalSizeInitializedFuture()
      val initialSize = initialSizeFuture.await()
      val optionsWithSize = if (initialSize != null) {
        options.builder().initialTermSize(initialSize).build()
      }
      else options

      val session = withContext(Dispatchers.IO) {
        startTerminalSession(project, optionsWithSize)
      }

      widget.connectToSession(session)
    }
  }

  private suspend fun startTerminalSession(project: Project, options: ShellStartupOptions): TerminalSession {
    val api = TerminalSessionsManagerApi.getInstance()
    val sessionId = api.startTerminalSession(project.projectId(), options.toDto())
    return FrontendTerminalSession(sessionId)
  }
}