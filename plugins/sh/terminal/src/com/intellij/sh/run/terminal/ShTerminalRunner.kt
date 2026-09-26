// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.run.terminal

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.rpc.topics.sendToClient
import com.intellij.sh.run.ShRunner
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory

/**
 * The [ShRunner] of every product mode: it does not touch the terminal itself but sends a [ShTerminalRunRequest] to the
 * frontend of the current session through [ShTerminalRunTopic]. The request is handed over on the calling thread, so the
 * caller's `ClientId` decides which client receives it; the delivery itself is asynchronous.
 */
@ApiStatus.Internal
class ShTerminalRunner : ShRunner {
  override fun run(
    project: Project,
    command: String,
    workingDirectory: String,
    title: String,
    activateToolWindow: Boolean,
  ) {
    ShTerminalRunTopic.TOPIC.sendToClient(project, ShTerminalRunRequest(command, workingDirectory, title, activateToolWindow))
  }

  override fun isAvailable(project: Project): Boolean {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
    return toolWindow != null && toolWindow.isAvailable()
  }
}
