// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ToolWindowMoveAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.statistics.PythonPackagesToolwindowStatisticsCollector
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
      AllIcons.Actions.MoveToBottomLeft to PyBundle.message("python.toolwindow.packages.move.to.bottom.action")
    }
    else {
      AllIcons.Actions.MoveToRightBottom to PyBundle.message("python.toolwindow.packages.move.to.right.action")
    }
    e.presentation.icon = icon
    e.presentation.text = text
  }

  override fun actionPerformed(e: AnActionEvent) {
    val toolWindow = findToolWindow(e) ?: return
    // The action only swaps between the two corners its two icons point to, so encoding "from" as
    // whichever of those two matches the current basic anchor is exact — no need to reach into
    // `WindowInfo` (internal API) to distinguish split states we never produce ourselves.
    val (from, to) = if (toolWindow.anchor == ToolWindowAnchor.RIGHT) {
      ToolWindowMoveAction.Anchor.RightBottom to ToolWindowMoveAction.Anchor.BottomLeft
    }
    else {
      ToolWindowMoveAction.Anchor.BottomLeft to ToolWindowMoveAction.Anchor.RightBottom
    }
    to.applyTo(toolWindow)
    PythonPackagesToolwindowStatisticsCollector.anchorToggledEvent.log(from, to)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun findToolWindow(e: AnActionEvent): ToolWindow? {
    val project = e.project ?: return null
    return ToolWindowManager.getInstance(project).getToolWindow(PyPackagingToolWindowPanel.PY_PACKAGES_TOOL_WINDOW_ID)
  }
}
