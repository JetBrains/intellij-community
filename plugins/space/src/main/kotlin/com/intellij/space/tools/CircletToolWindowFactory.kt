package com.intellij.space.tools

import com.intellij.space.plugins.pipelines.ui.CircletToolWindowService
import com.intellij.space.utils.computeSafe
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

class CircletToolWindowFactory : ToolWindowFactory, DumbAware {

  override fun shouldBeAvailable(project: Project): Boolean {
    return false
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val service = project.service<CircletToolWindowService>()
    service.createToolWindowContent(toolWindow)
  }

  override fun isDoNotActivateOnStart(): Boolean {
    return true
  }

  companion object {
    const val TOOL_WINDOW_ID = "Space Automation"
  }

}

val Project.spaceKtsToolwindow: ToolWindow?
  get() = computeSafe {
    toolWindowManager.getToolWindow(CircletToolWindowFactory.TOOL_WINDOW_ID)
  }
