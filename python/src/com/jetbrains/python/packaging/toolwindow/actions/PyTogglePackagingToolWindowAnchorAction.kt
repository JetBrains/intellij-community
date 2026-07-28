// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowPanel

internal class PyTogglePackagingToolWindowAnchorAction : DumbAwareAction() {

  override fun update(e: AnActionEvent) {
    val toolWindow = findToolWindow(e)
    if (toolWindow == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isEnabledAndVisible = true
    val (icon, text) = if (toolWindow.anchor == ToolWindowAnchor.RIGHT) {
      AllIcons.Actions.MoveToBottomRight to PyBundle.message("python.toolwindow.packages.move.to.bottom.action")
    }
    else {
      AllIcons.Actions.MoveToRightBottom to PyBundle.message("python.toolwindow.packages.move.to.right.action")
    }
    e.presentation.icon = icon
    e.presentation.text = text
  }

  override fun actionPerformed(e: AnActionEvent) {
    val toolWindow = findToolWindow(e) ?: return
    val target = if (toolWindow.anchor == ToolWindowAnchor.RIGHT) ToolWindowAnchor.BOTTOM else ToolWindowAnchor.RIGHT
    toolWindow.setAnchor(target, null)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun findToolWindow(e: AnActionEvent): ToolWindow? {
    val project = e.project ?: return null
    return ToolWindowManager.getInstance(project).getToolWindow(PyPackagingToolWindowPanel.PY_PACKAGES_TOOL_WINDOW_ID)
  }
}
