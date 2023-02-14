// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class PyPackagesToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val service = project.service<PyPackagingToolWindowService>()
    val toolWindowPanel = PyPackagingToolWindowPanel(project, toolWindow)
    service.initialize(toolWindowPanel)
    val content = ContentFactory.getInstance().createContent(toolWindowPanel.component, null, false)
    toolWindow.contentManager.addContent(content)
  }
}


