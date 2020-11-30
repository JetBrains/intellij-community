// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.tools

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.space.plugins.pipelines.ui.SpaceToolWindowService
import com.intellij.space.utils.computeSafe

class SpaceToolWindowFactory : ToolWindowFactory, DumbAware {

  override fun shouldBeAvailable(project: Project): Boolean {
    return false
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val service = project.service<SpaceToolWindowService>()
    service.createToolWindowContent(toolWindow)
  }

  override fun isDoNotActivateOnStart(): Boolean {
    return true
  }

  companion object {
    const val TOOL_WINDOW_ID = "Space Automation" // NON-NLS
  }

}

val Project.spaceKtsToolwindow: ToolWindow?
  get() = computeSafe {
    toolWindowManager.getToolWindow(SpaceToolWindowFactory.TOOL_WINDOW_ID)
  }
