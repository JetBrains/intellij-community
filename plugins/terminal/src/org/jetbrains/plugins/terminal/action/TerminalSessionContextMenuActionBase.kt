// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContextMenuActionBase
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.ui.TerminalContainer

abstract class TerminalSessionContextMenuActionBase : ToolWindowContextMenuActionBase(), ActionRemoteBehaviorSpecification.Frontend {
  final override fun update(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    val project = e.project
    if (project != null && TerminalToolWindowManager.isTerminalToolWindow(toolWindow) && content != null) {
      val terminalWidget = findContextTerminal(e, content)
      if (terminalWidget != null) {
        updateInTerminalToolWindow(e, project, content, terminalWidget)
      }
      else {
        e.presentation.isEnabledAndVisible = false
      }
    }
    else {
      e.presentation.isEnabledAndVisible = false
    }
  }

  open fun updateInTerminalToolWindow(e: AnActionEvent, project: Project, content: Content, terminalWidget: TerminalWidget) {}

  final override fun actionPerformed(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    val project = e.project
    if (project != null && TerminalToolWindowManager.isTerminalToolWindow(toolWindow) && content != null) {
      val terminalWidget = findContextTerminal(e, content)
      if (terminalWidget != null) {
        actionPerformedInTerminalToolWindow(e, project, content, terminalWidget)
      }
    }
  }

  abstract fun actionPerformedInTerminalToolWindow(e: AnActionEvent, project: Project, content: Content, terminalWidget: TerminalWidget)

  private fun findContextTerminal(e: AnActionEvent, content: Content): TerminalWidget? {
    val newWidget = e.dataContext.getData(TerminalContainer.TERMINAL_WIDGET_DATA_KEY)
    if (newWidget != null) return newWidget
    val terminalWidget = e.dataContext.getData(JBTerminalWidget.TERMINAL_DATA_KEY)
    return if (terminalWidget != null && UIUtil.isAncestor(content.component, terminalWidget)) {
      terminalWidget.asNewWidget()
    }
    else {
      TerminalToolWindowManager.findWidgetByContent(content)
    }
  }
}