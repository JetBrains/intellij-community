// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContextMenuActionBase
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory

abstract class TerminalSessionContextMenuActionBase : ToolWindowContextMenuActionBase() {
  final override fun update(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    val project = e.project
    if (project != null && TerminalToolWindowFactory.TOOL_WINDOW_ID == toolWindow.id && content != null) {
      updateInTerminalToolWindow(e, project, content)
    }
    else {
      e.presentation.isEnabledAndVisible = false
    }
  }

  open fun updateInTerminalToolWindow(e: AnActionEvent, project: Project, content: Content) {}

  final override fun actionPerformed(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    val project = e.project
    if (project != null && TerminalToolWindowFactory.TOOL_WINDOW_ID == toolWindow.id && content != null) {
      actionPerformedInTerminalToolWindow(e, project, content)
    }
  }

  abstract fun actionPerformedInTerminalToolWindow(e: AnActionEvent, project: Project, content: Content)
}